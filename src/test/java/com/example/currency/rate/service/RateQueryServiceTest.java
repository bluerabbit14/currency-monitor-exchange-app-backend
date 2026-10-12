package com.example.currency.rate.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.dto.RateHistoryResponse;
import com.example.currency.rate.dto.RateResponse;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
import com.example.currency.rate.exception.InvalidRateHistoryRangeException;
import com.example.currency.rate.exception.RateNotAvailableException;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.rate.repository.ExchangeRateRepository;
import com.example.currency.rate.repository.RateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

	@Mock
	private ExchangeRateRepository exchangeRateRepository;

	@Mock
	private RateHistoryRepository rateHistoryRepository;

	@Mock
	private AssetPairRepository assetPairRepository;

	private RateQueryService rateQueryService;

	@BeforeEach
	void setUp() {
		RateProperties properties = new RateProperties();
		properties.setStaleAfter(Duration.ofMinutes(15));
		rateQueryService = new RateQueryService(
				exchangeRateRepository,
				rateHistoryRepository,
				assetPairRepository,
				properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void retrievesExistingCachedRateAsFresh() {
		AssetPair pair = pair("USD", "INR", AssetType.FIAT);
		ExchangeRate rate = rate(pair, "83.1", NOW.minus(Duration.ofMinutes(5)));
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findByPairIdWithAssets(pair.getId())).thenReturn(Optional.of(rate));

		RateResponse response = rateQueryService.getCurrentRate("USD", "INR");

		assertEquals("USD", response.base());
		assertEquals("INR", response.quote());
		assertEquals(new BigDecimal("83.1"), response.rate());
		assertFalse(response.stale());
		assertEquals("test-provider", response.providerCode());
	}

	@Test
	void returnsStaleCachedRate() {
		AssetPair pair = pair("BTC", "USD", AssetType.CRYPTO);
		ExchangeRate rate = rate(pair, "65000", NOW.minus(Duration.ofMinutes(30)));
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "BTC", "USD"))
				.thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findByPairIdWithAssets(pair.getId())).thenReturn(Optional.of(rate));

		RateResponse response = rateQueryService.getCurrentRate("btc", "usd");

		assertTrue(response.stale());
		assertEquals(new BigDecimal("65000"), response.rate());
	}

	@Test
	void configuredPairWithoutCacheIsNotAvailable() {
		AssetPair pair = pair("USD", "INR", AssetType.FIAT);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findByPairIdWithAssets(pair.getId())).thenReturn(Optional.empty());

		assertThrows(RateNotAvailableException.class, () -> rateQueryService.getCurrentRate("USD", "INR"));
	}

	@Test
	void unsupportedPairIsRejected() {
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "AAA", "BBB"))
				.thenReturn(Optional.empty());

		assertThrows(UnsupportedPairException.class, () -> rateQueryService.getCurrentRate("AAA", "BBB"));
	}

	@Test
	void listsAndFiltersByTypeBaseAndQuote() {
		AssetPair pair = pair("BTC", "USD", AssetType.CRYPTO);
		ExchangeRate rate = rate(pair, "65000", NOW.minus(Duration.ofMinutes(1)));
		Pageable pageable = PageRequest.of(0, 20);
		when(exchangeRateRepository.findCurrentRates(
				eq(AssetStatus.ACTIVE),
				eq(AssetType.CRYPTO),
				eq("BTC"),
				eq("USD"),
				eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(rate), pageable, 1));

		Page<RateResponse> page = rateQueryService.listCurrentRates(AssetType.CRYPTO, "BTC", "USD", pageable);

		assertEquals(1, page.getTotalElements());
		assertEquals("BTC", page.getContent().getFirst().base());
		assertEquals("USD", page.getContent().getFirst().quote());
	}

	@Test
	void returnsHistoryInPageOrder() {
		AssetPair pair = pair("USD", "INR", AssetType.FIAT);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));
		RateHistory newer = history(pair, "84", NOW.minus(Duration.ofHours(1)));
		RateHistory older = history(pair, "83", NOW.minus(Duration.ofHours(2)));
		Pageable pageable = PageRequest.of(0, 100);
		Instant from = Instant.parse("2026-10-01T00:00:00Z");
		Instant to = Instant.parse("2026-10-12T00:00:00Z");
		when(rateHistoryRepository.findHistory(pair.getId(), from, to, pageable))
				.thenReturn(new PageImpl<>(List.of(newer, older), pageable, 2));

		Page<RateHistoryResponse> page = rateQueryService.getHistory("USD", "INR", from, to, pageable);

		assertEquals(2, page.getTotalElements());
		assertEquals(new BigDecimal("84"), page.getContent().get(0).rate());
		assertEquals(new BigDecimal("83"), page.getContent().get(1).rate());
	}

	@Test
	void rejectsInvalidHistoryRange() {
		AssetPair pair = pair("USD", "INR", AssetType.FIAT);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));

		assertThrows(
				InvalidRateHistoryRangeException.class,
				() -> rateQueryService.getHistory(
						"USD",
						"INR",
						Instant.parse("2026-10-12T00:00:00Z"),
						Instant.parse("2026-10-01T00:00:00Z"),
						PageRequest.of(0, 100)));
	}

	@Test
	void appliesDefaultHistoryWindow() {
		AssetPair pair = pair("USD", "INR", AssetType.FIAT);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));
		when(rateHistoryRepository.findHistory(eq(pair.getId()), any(), any(), any()))
				.thenReturn(Page.empty());

		rateQueryService.getHistory("USD", "INR", null, null, PageRequest.of(0, 100));

		ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
		verify(rateHistoryRepository).findHistory(eq(pair.getId()), from.capture(), to.capture(), any());
		assertEquals(NOW, to.getValue());
		assertEquals(NOW.minus(Duration.ofDays(30)), from.getValue());
	}

	@Test
	void normalizesBlankFilterSymbolsToNull() {
		Pageable pageable = PageRequest.of(0, 20);
		when(exchangeRateRepository.findCurrentRates(
				eq(AssetStatus.ACTIVE),
				isNull(),
				isNull(),
				isNull(),
				eq(pageable)))
				.thenReturn(Page.empty());

		rateQueryService.listCurrentRates(null, "  ", "", pageable);

		verify(exchangeRateRepository).findCurrentRates(
				AssetStatus.ACTIVE,
				null,
				null,
				null,
				pageable);
	}

	private static AssetPair pair(String baseSymbol, String quoteSymbol, AssetType baseType) {
		Asset base = asset(baseSymbol, baseType);
		Asset quote = asset(quoteSymbol, AssetType.FIAT);
		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setBaseAsset(base);
		pair.setQuoteAsset(quote);
		pair.setSymbol(baseSymbol + "/" + quoteSymbol);
		pair.setStatus(AssetStatus.ACTIVE);
		return pair;
	}

	private static Asset asset(String symbol, AssetType type) {
		Asset asset = new Asset();
		setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(symbol);
		asset.setAssetType(type);
		asset.setStatus(AssetStatus.ACTIVE);
		asset.setDecimalScale((short) 2);
		return asset;
	}

	private static ExchangeRate rate(AssetPair pair, String value, Instant observedAt) {
		ExchangeRate rate = new ExchangeRate();
		rate.setPair(pair);
		setField(rate, "pairId", pair.getId());
		rate.setRate(new BigDecimal(value));
		rate.setObservedAt(observedAt);
		rate.setFetchedAt(observedAt.plusSeconds(5));
		rate.setProviderCode("test-provider");
		return rate;
	}

	private static RateHistory history(AssetPair pair, String value, Instant observedAt) {
		RateHistory history = new RateHistory();
		setField(history, "id", UUID.randomUUID());
		history.setPair(pair);
		history.setRate(new BigDecimal(value));
		history.setObservedAt(observedAt);
		history.setFetchedAt(observedAt.plusSeconds(1));
		history.setProviderCode("test-provider");
		return history;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> type = target.getClass();
		while (type != null) {
			try {
				var field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(target, value);
				return;
			}
			catch (NoSuchFieldException ex) {
				type = type.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new IllegalStateException(name);
	}

}
