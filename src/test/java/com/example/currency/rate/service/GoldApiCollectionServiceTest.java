package com.example.currency.rate.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.exception.InvalidRateObservationException;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProvider;
import com.example.currency.rate.provider.RateProviderException;
import com.example.currency.rate.provider.goldapi.GoldApiClient;
import com.example.currency.rate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldApiCollectionServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
	private static final Instant PROVIDER_TIME = Instant.parse("2026-10-17T09:59:30Z");

	@Mock
	private AssetPairRepository assetPairRepository;

	@Mock
	private ExchangeRateRepository exchangeRateRepository;

	@Mock
	private RatePersistenceService ratePersistenceService;

	@Mock
	private GoldApiClient client;

	private GoldApiCollectionService service;

	@BeforeEach
	void setUp() {
		service = new GoldApiCollectionService(assetPairRepository, exchangeRateRepository, ratePersistenceService,
				client, Clock.fixed(NOW, ZoneOffset.UTC));
		when(client.remainingRequests()).thenReturn(Integer.MAX_VALUE);
	}

	@Test
	void fetchesEachMappedCommodityAndRecordsItWithTheProviderTime() {
		AssetPair xau = pair("XAU");
		AssetPair xag = pair("XAG");
		AssetPair xpt = pair("XPT");
		AssetPair xpd = pair("XPD");
		givenPairs(xau, xag, xpt, xpd);
		for (String symbol : List.of("XAU", "XAG", "XPT", "XPD")) {
			when(client.fetchQuote(symbol)).thenReturn(new ProviderQuote(new BigDecimal("100.5"), PROVIDER_TIME, null));
		}

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(4, 0, 0, 0, 0, false), summary);
		var order = inOrder(client);
		order.verify(client).fetchQuote("XAU");
		order.verify(client).fetchQuote("XAG");
		order.verify(client).fetchQuote("XPT");
		order.verify(client).fetchQuote("XPD");
		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService, times(4)).recordObservation(captor.capture());
		RateObservation first = captor.getAllValues().get(0);
		assertEquals(xau.getId(), first.pairId());
		assertEquals(PROVIDER_TIME, first.observedAt());
		assertEquals(NOW, first.fetchedAt());
		assertEquals("GOLDAPI", first.providerCode());
		assertNull(first.providerObservationId());
	}

	@Test
	void usesFetchTimeWhenTheProviderSuppliesNoTimestamp() {
		givenPairs(pair("XAU"));
		when(client.fetchQuote("XAU")).thenReturn(new ProviderQuote(BigDecimal.TEN, null, null));

		service.collect();

		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		assertEquals(NOW, captor.getValue().observedAt());
	}

	@Test
	void skipsPricesNotNewerThanTheStoredOneSoHistoryHasNoRepeats() {
		AssetPair xau = pair("XAU");
		AssetPair xag = pair("XAG");
		givenPairs(xau, xag);
		ExchangeRate stored = mock(ExchangeRate.class);
		when(stored.getPairId()).thenReturn(xau.getId());
		when(stored.getObservedAt()).thenReturn(PROVIDER_TIME);
		when(exchangeRateRepository.findAllById(any())).thenReturn(List.of(stored));
		when(client.fetchQuote(anyString())).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 1, 0, 0, false), summary);
		verify(ratePersistenceService, times(1)).recordObservation(argThat(o -> o.pairId().equals(xag.getId())));
	}

	@Test
	void oneFailingCommodityDoesNotStopTheOthers() {
		givenPairs(pair("XAU"), pair("XAG"), pair("XPT"), pair("XPD"));
		when(client.fetchQuote("XAU")).thenThrow(new RateProviderException("Gold API error HTTP 502"));
		when(client.fetchQuote("XAG")).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));
		when(client.fetchQuote("XPT")).thenThrow(new RateProviderException("malformed"));
		when(client.fetchQuote("XPD")).thenReturn(new ProviderQuote(BigDecimal.TEN, PROVIDER_TIME, null));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(2, 2, 0, 0, 0, false), summary);
		verify(ratePersistenceService, times(2)).recordObservation(any());
	}

	@Test
	void aPersistenceFailureForOnePairDoesNotStopTheOthers() {
		AssetPair xau = pair("XAU");
		givenPairs(xau, pair("XAG"));
		when(client.fetchQuote(anyString())).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));
		doThrow(new InvalidRateObservationException("bad")).when(ratePersistenceService)
				.recordObservation(argThat(o -> o.pairId().equals(xau.getId())));

		assertEquals(new CollectionSummary(1, 1, 0, 0, 0, false), service.collect());
	}

	@Test
	void aRateLimitEndsTheRunWithoutFurtherRequestsOrRetries() {
		givenPairs(pair("XAU"), pair("XAG"), pair("XPT"));
		when(client.fetchQuote("XAU")).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));
		when(client.fetchQuote("XAG")).thenThrow(new ProviderRateLimitException("limit", NOW.plusSeconds(120)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 0, 1, 1, false), summary);
		verify(client, never()).fetchQuote("XPT");
		verify(client, times(1)).fetchQuote("XAG");
		verify(ratePersistenceService, times(1)).recordObservation(any());
	}

	@Test
	void onlyCollectsPairsWhoseMappingAgreesWithTheirAssets() {
		AssetPair good = pair("XAU");
		AssetPair wrongExternalId = pair("XAG");
		wrongExternalId.getBaseAsset().setExternalId("XPT");
		AssetPair notCommodity = pair("XPT");
		notCommodity.getBaseAsset().setAssetType(AssetType.CRYPTO);
		AssetPair otherProvider = pair("XPD");
		otherProvider.getBaseAsset().setProvider("OTHER");
		AssetPair eurQuote = pair("HG");
		eurQuote.getQuoteAsset().setCurrencyCode("EUR");
		AssetPair inactiveBase = pair("XAU2");
		inactiveBase.getBaseAsset().setStatus(AssetStatus.INACTIVE);
		AssetPair blank = pair("XAG2");
		blank.setProviderPairCode(" ");
		AssetPair duplicate = pair("XAU");
		givenPairs(good, wrongExternalId, notCommodity, otherProvider, eurQuote, inactiveBase, blank, duplicate);
		when(client.fetchQuote("XAU")).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 7, 0, 0, false), summary);
		verify(client, times(1)).fetchQuote(anyString());
	}


	@Test
	void fetchesOnlyWhatTheHourlyBudgetAllowsAndNeverProvokesARefusal() {
		givenPairs(pair("XAU"), pair("XAG"), pair("XPT"), pair("XPD"));
		when(client.remainingRequests()).thenReturn(2, 1, 0);
		when(client.fetchQuote(anyString())).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(2, 0, 0, 0, 2, false), summary);
		verify(client, times(1)).fetchQuote("XAU");
		verify(client, times(1)).fetchQuote("XAG");
		verify(client, never()).fetchQuote("XPT");
		verify(client, never()).fetchQuote("XPD");
	}

	@Test
	void rotatesThroughEveryCommodityAcrossRunsWhenTheBudgetIsSmall() {
		givenPairs(pair("XAU"), pair("XAG"), pair("XPT"), pair("XPD"));
		when(client.remainingRequests()).thenReturn(1, 0, 1, 0, 1, 0, 1, 0);
		when(client.fetchQuote(anyString())).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));

		for (int run = 0; run < 4; run++) {
			service.collect();
		}

		var order = inOrder(client);
		order.verify(client).fetchQuote("XAU");
		order.verify(client).fetchQuote("XAG");
		order.verify(client).fetchQuote("XPT");
		order.verify(client).fetchQuote("XPD");
		verify(client, times(4)).fetchQuote(anyString());
	}

	@Test
	void aRateLimitedCommodityIsFirstInLineNextRun() {
		givenPairs(pair("XAU"), pair("XAG"));
		when(client.fetchQuote("XAU")).thenThrow(new ProviderRateLimitException("limit", NOW.plusSeconds(120)));

		service.collect();
		org.mockito.Mockito.doReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null)).when(client).fetchQuote("XAU");
		org.mockito.Mockito.doReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null)).when(client).fetchQuote("XAG");
		when(client.fetchQuote("XAG")).thenReturn(new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));
		service.collect();

		var order = inOrder(client);
		order.verify(client, times(2)).fetchQuote("XAU");
		order.verify(client).fetchQuote("XAG");
	}
	@Test
	void emptyConfigurationMakesNoRequest() {
		givenPairs();

		assertEquals(new CollectionSummary(0, 0, 0, 0, 0, false), service.collect());

		verifyNoInteractions(client, ratePersistenceService);
	}

	@Test
	void overlappingRunReturnsImmediatelyWithoutASecondRequest() throws Exception {
		CountDownLatch inFetch = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		givenPairs(pair("XAU"));
		when(client.fetchQuote("XAU")).thenAnswer(invocation -> {
			inFetch.countDown();
			assertTrue(release.await(5, TimeUnit.SECONDS));
			return new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null);
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<CollectionSummary> first = executor.submit(service::collect);
			assertTrue(inFetch.await(5, TimeUnit.SECONDS));

			assertTrue(service.collect().overlapped());
			release.countDown();
			assertEquals(1, first.get(5, TimeUnit.SECONDS).succeeded());
			verify(client, times(1)).fetchQuote(anyString());
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void theTwelveDataCollectorStillIgnoresGoldApiPairs() {
		RateProvider twelveData = mock(RateProvider.class);
		when(twelveData.code()).thenReturn("TWELVEDATA");
		RateCollectionService twelveDataService = new RateCollectionService(
				assetPairRepository, ratePersistenceService, List.of(twelveData), Clock.fixed(NOW, ZoneOffset.UTC));
		AssetPair xau = pair("XAU");
		AssetPair eurUsd = new AssetPair();
		setField(eurUsd, "id", UUID.randomUUID());
		eurUsd.setSymbol("EUR/USD");
		eurUsd.setProviderCode("TWELVEDATA");
		eurUsd.setProviderPairCode("EUR/USD");
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(xau, eurUsd));
		when(twelveData.isConfigured()).thenReturn(true);
		when(twelveData.fetchQuote("EUR/USD")).thenReturn(new ProviderQuote(new BigDecimal("1.1"), null, null));

		assertEquals(new CollectionSummary(1, 0, 1, 0, 0, false), twelveDataService.collect());

		verify(twelveData, times(1)).fetchQuote(anyString());
		verifyNoInteractions(client);
	}

	private void givenPairs(AssetPair... pairs) {
		when(assetPairRepository.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, "GOLDAPI"))
				.thenReturn(List.of(pairs));
	}

	private static AssetPair pair(String symbol) {
		Asset base = new Asset();
		base.setSymbol(symbol);
		base.setAssetType(AssetType.COMMODITY);
		base.setStatus(AssetStatus.ACTIVE);
		base.setProvider("GOLDAPI");
		base.setExternalId(symbol);
		Asset usd = new Asset();
		usd.setSymbol("USD");
		usd.setAssetType(AssetType.FIAT);
		usd.setStatus(AssetStatus.ACTIVE);
		usd.setCurrencyCode("USD");
		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setBaseAsset(base);
		pair.setQuoteAsset(usd);
		pair.setSymbol(symbol + "/USD");
		pair.setStatus(AssetStatus.ACTIVE);
		pair.setProviderCode("GOLDAPI");
		pair.setProviderPairCode(symbol);
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
