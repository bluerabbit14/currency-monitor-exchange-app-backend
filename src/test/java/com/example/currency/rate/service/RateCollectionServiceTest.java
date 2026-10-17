package com.example.currency.rate.service;

import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.exception.InvalidRateObservationException;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProvider;
import com.example.currency.rate.provider.RateProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateCollectionServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

	@Mock
	private AssetPairRepository assetPairRepository;

	@Mock
	private RatePersistenceService ratePersistenceService;

	@Mock
	private RateProvider provider;

	private RateCollectionService service;

	@BeforeEach
	void setUp() {
		when(provider.code()).thenReturn("TWELVEDATA");
		service = new RateCollectionService(
				assetPairRepository, ratePersistenceService, List.of(provider), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void recordsEachMappedPairWithFetchTimeAsObservedAtFallback() {
		AssetPair usdInr = pair("USD/INR", "TWELVEDATA", "USD/INR");
		when(provider.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(usdInr));
		when(provider.fetchQuote("USD/INR"))
				.thenReturn(new ProviderQuote(new BigDecimal("83.25"), null, null));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 0, 0, 0, false), summary);
		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		RateObservation observation = captor.getValue();
		assertEquals(usdInr.getId(), observation.pairId());
		assertEquals(new BigDecimal("83.25"), observation.rate());
		assertEquals(NOW, observation.fetchedAt());
		assertEquals(NOW, observation.observedAt());
		assertEquals("TWELVEDATA", observation.providerCode());
	}

	@Test
	void keepsProviderObservationTimeWhenSupplied() {
		Instant providerTime = NOW.minusSeconds(30);
		when(provider.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE))
				.thenReturn(List.of(pair("EUR/USD", "TWELVEDATA", "EUR/USD")));
		when(provider.fetchQuote("EUR/USD"))
				.thenReturn(new ProviderQuote(new BigDecimal("1.08"), providerTime, "obs-9"));

		service.collect();

		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		assertEquals(providerTime, captor.getValue().observedAt());
		assertEquals(NOW, captor.getValue().fetchedAt());
		assertEquals("obs-9", captor.getValue().providerObservationId());
	}

	@Test
	void doesNotFetchUnmappedUnknownProviderOrBlankCodePairs() {
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(
				pair("GOLD/USD", null, null),
				pair("SILVER/USD", " ", null),
				pair("BTC/USD", "COINGECKO", "bitcoin:usd"),
				pair("GBP/USD", "TWELVEDATA", " "),
				pair("ETH/USD", "TWELVEDATA", null)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(0, 0, 5, 0, 0, false), summary);
		verify(provider, never()).fetchQuote(anyString());
		verifyNoInteractions(ratePersistenceService);
	}

	@Test
	void skipsAProviderThatIsNotConfigured() {
		when(provider.isConfigured()).thenReturn(false);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE))
				.thenReturn(List.of(pair("EUR/USD", "TWELVEDATA", "EUR/USD")));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(0, 0, 1, 0, 0, false), summary);
		verify(provider, never()).fetchQuote(anyString());
	}

	@Test
	void oneFailingPairDoesNotStopTheOthers() {
		AssetPair broken = pair("USD/INR", "TWELVEDATA", "USD/INR");
		AssetPair invalid = pair("EUR/USD", "TWELVEDATA", "EUR/USD");
		AssetPair good = pair("GBP/USD", "TWELVEDATA", "GBP/USD");
		when(provider.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(broken, invalid, good));
		when(provider.fetchQuote("USD/INR")).thenThrow(new RateProviderException("timeout"));
		when(provider.fetchQuote("EUR/USD")).thenReturn(new ProviderQuote(new BigDecimal("1.1"), null, null));
		when(provider.fetchQuote("GBP/USD")).thenReturn(new ProviderQuote(new BigDecimal("1.3"), null, null));
		doThrow(new InvalidRateObservationException("rejected"))
				.when(ratePersistenceService).recordObservation(
						org.mockito.ArgumentMatchers.argThat(o -> o != null && o.pairId().equals(invalid.getId())));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 2, 0, 0, 0, false), summary);
		verify(ratePersistenceService, times(2)).recordObservation(any());
		verify(ratePersistenceService, never()).recordObservation(
				org.mockito.ArgumentMatchers.argThat(o -> o != null && o.pairId().equals(broken.getId())));
	}

	@Test
	void rateLimitStopsFurtherRequestsInTheRun() {
		when(provider.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(
				pair("USD/INR", "TWELVEDATA", "USD/INR"),
				pair("EUR/USD", "TWELVEDATA", "EUR/USD"),
				pair("GBP/USD", "TWELVEDATA", "GBP/USD")));
		when(provider.fetchQuote("USD/INR")).thenReturn(new ProviderQuote(new BigDecimal("83"), null, null));
		when(provider.fetchQuote("EUR/USD")).thenThrow(new ProviderRateLimitException("limit", NOW.plusSeconds(120)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 0, 1, 1, false), summary);
		verify(provider, never()).fetchQuote("GBP/USD");
		verify(ratePersistenceService, times(1)).recordObservation(any());
	}

	@Test
	void overlappingRunReturnsImmediatelyWithoutTouchingTheProvider() throws Exception {
		CountDownLatch inFetch = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(provider.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE))
				.thenReturn(List.of(pair("EUR/USD", "TWELVEDATA", "EUR/USD")));
		when(provider.fetchQuote(eq("EUR/USD"))).thenAnswer(invocation -> {
			inFetch.countDown();
			assertTrue(release.await(5, TimeUnit.SECONDS));
			return new ProviderQuote(new BigDecimal("1.1"), null, null);
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<CollectionSummary> first = executor.submit(service::collect);
			assertTrue(inFetch.await(5, TimeUnit.SECONDS));

			CollectionSummary second = service.collect();

			assertTrue(second.overlapped());
			release.countDown();
			assertEquals(1, first.get(5, TimeUnit.SECONDS).succeeded());
			verify(provider, times(1)).fetchQuote(anyString());
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	private static AssetPair pair(String symbol, String providerCode, String providerPairCode) {
		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setSymbol(symbol);
		pair.setProviderCode(providerCode);
		pair.setProviderPairCode(providerPairCode);
		return pair;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			var field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
