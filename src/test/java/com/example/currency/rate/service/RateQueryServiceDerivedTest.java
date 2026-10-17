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
import com.example.currency.rate.exception.RateNotAvailableException;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.rate.repository.ExchangeRateRepository;
import com.example.currency.rate.repository.RateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateQueryServiceDerivedTest {

	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
	private static final Instant FRESH = NOW.minus(Duration.ofMinutes(2));

	@Mock
	private ExchangeRateRepository exchangeRateRepository;

	@Mock
	private RateHistoryRepository rateHistoryRepository;

	@Mock
	private AssetPairRepository assetPairRepository;

	private RateQueryService service;

	private final Asset usd = asset("USD", AssetType.FIAT, 2);
	private final Asset inr = asset("INR", AssetType.FIAT, 2);
	private final Asset eur = asset("EUR", AssetType.FIAT, 2);
	private final Asset btc = asset("BTC", AssetType.CRYPTO, 8);
	private final Asset gold = asset("GOLD", AssetType.COMMODITY, 2);

	@BeforeEach
	void setUp() {
		RateProperties properties = new RateProperties();
		properties.setStaleAfter(Duration.ofMinutes(15));
		service = new RateQueryService(exchangeRateRepository, rateHistoryRepository, assetPairRepository,
				properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void convertsCryptoUsdWithAUsdToFiatPair() {
		givenPair(btc, usd, "100000", FRESH);
		givenPair(usd, inr, "90", FRESH);

		RateResponse response = service.getCurrentRate("btc", "inr");

		assertEquals("BTC", response.base());
		assertEquals("INR", response.quote());
		assertEquals(new BigDecimal("9000000.00"), response.rate());
		assertFalse(response.stale());
		assertEquals(FRESH, response.observedAt());
		assertEquals("COINGECKO+TWELVEDATA", response.providerCode());
	}

	@Test
	void convertsCryptoUsdWithAnInvertedFiatToUsdPair() {
		givenPair(btc, usd, "100000", FRESH);
		givenPair(eur, usd, "1.25", FRESH);

		RateResponse response = service.getCurrentRate("BTC", "EUR");

		assertEquals("EUR", response.quote());
		assertEquals(new BigDecimal("80000.00"), response.rate());
	}

	@Test
	void keepsTheStablecoinMarketPrice() {
		Asset usdt = asset("USDT", AssetType.CRYPTO, 8);
		givenPair(usdt, usd, "0.9991", FRESH);
		givenPair(usd, inr, "90", FRESH);

		assertEquals(new BigDecimal("89.92"), service.getCurrentRate("USDT", "INR").rate());
	}

	@Test
	void directPairsAreServedAsBeforeWithoutDerivation() {
		givenPair(btc, usd, "100000", FRESH);

		RateResponse response = service.getCurrentRate("BTC", "USD");

		assertEquals(new BigDecimal("100000"), response.rate());
		assertEquals("USD", response.quote());
		assertEquals("COINGECKO", response.providerCode());
	}

	@Test
	void unsupportedFiatCurrencyIsAnUnsupportedPair() {
		givenPair(btc, usd, "100000", FRESH);

		UnsupportedPairException ex = assertThrows(UnsupportedPairException.class,
				() -> service.getCurrentRate("BTC", "JPY"));

		assertEquals("Unsupported pair: BTC/JPY", ex.getMessage());
	}

	@Test
	void commoditiesCryptoQuotesAndSameAssetAreNotDerived() {
		givenPair(btc, usd, "100000", FRESH);
		givenPair(gold, usd, "2000", FRESH);
		givenPair(eur, usd, "1.25", FRESH);
		givenPair(usd, inr, "90", FRESH);

		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("GOLD", "INR"));
		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("BTC", "BTC"));
		// A crypto asset is not a fiat quote even if a USD pair for it exists.
		givenPair(btc, usd, "100000", FRESH);
		givenPair(asset("ETH", AssetType.CRYPTO, 8), usd, "3000", FRESH);
		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("BTC", "ETH"));
	}

	@Test
	void derivesFiatCrossPairsFromTheirUsdLegsInEveryStoredDirection() {
		Asset jpy = asset("JPY", AssetType.FIAT, 0);
		Asset aed = asset("AED", AssetType.FIAT, 2);
		givenPair(eur, usd, "1.25", FRESH);        // EUR/USD: 1 EUR = 1.25 USD
		givenPair(usd, inr, "90", FRESH);          // USD/INR: 1 USD = 90 INR
		givenPair(usd, jpy, "150", FRESH);         // USD/JPY: 1 USD = 150 JPY
		givenPair(usd, aed, "3.6725", FRESH);      // USD/AED

		// EUR/USD x USD/INR: both legs, mixed directions.
		assertEquals(new BigDecimal("112.50"), service.getCurrentRate("EUR", "INR").rate());
		// USD/INR stored, INR/USD requested: base INR (units per USD), quote USD.
		assertEquals(new BigDecimal("0.01"), service.getCurrentRate("INR", "USD").rate());
		// Two units-per-USD legs: INR -> JPY at the JPY scale of 0.
		assertEquals(new BigDecimal("2"), service.getCurrentRate("INR", "JPY").rate());
		assertEquals(new BigDecimal("24.51"), service.getCurrentRate("AED", "INR").rate());
		// EUR as a quote: USD/EUR is not stored (EUR/USD is), base USD needs only the quote leg.
		assertEquals(new BigDecimal("0.80"), service.getCurrentRate("USD", "EUR").rate());
		assertEquals(new BigDecimal("1.25"), service.getCurrentRate("EUR", "USD").rate());
	}

	@Test
	void fiatCrossPairsAreOnlyAsFreshAsTheirOldestLegAndNameBothProviders() {
		Instant oldFx = NOW.minus(Duration.ofHours(2));
		givenPair(eur, usd, "1.25", FRESH);
		givenPair(usd, inr, "90", oldFx);

		RateResponse response = service.getCurrentRate("EUR", "INR");

		assertTrue(response.stale());
		assertEquals(oldFx, response.observedAt());
		assertEquals("TWELVEDATA", response.providerCode());
	}

	@Test
	void unknownOrIdenticalFiatCrossPairsAreUnsupported() {
		givenPair(eur, usd, "1.25", FRESH);
		givenPair(usd, inr, "90", FRESH);

		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("EUR", "CHF"));
		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("CHF", "EUR"));
		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("EUR", "EUR"));
		assertThrows(UnsupportedPairException.class, () -> service.getCurrentRate("USD", "USD"));
	}

	@Test
	void fiatCrossHistoryUsesTheOtherLegsRateInForceAtEachTime() {
		AssetPair eurUsd = givenPair(eur, usd, "1", FRESH);
		AssetPair usdInr = givenPair(usd, inr, "1", FRESH);
		Instant t1 = Instant.parse("2026-10-12T10:00:00Z");
		Instant t2 = Instant.parse("2026-10-12T10:20:00Z");
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any())).thenReturn(new PageImpl<>(
				List.of(history(eurUsd, "1.30", t2), history(eurUsd, "1.25", t1)), pageable, 2));
		when(rateHistoryRepository.findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
				usdInr.getId(), t1)).thenReturn(Optional.of(history(usdInr, "80", t1.minusSeconds(60))));
		when(rateHistoryRepository.findBetweenAscending(usdInr.getId(), t1, t2))
				.thenReturn(List.of(history(usdInr, "90", Instant.parse("2026-10-12T10:10:00Z"))));

		Page<RateHistoryResponse> result = service.getHistory("EUR", "INR", t1.minusSeconds(1), NOW, pageable);

		assertEquals(List.of(new BigDecimal("117.00"), new BigDecimal("100.00")),
				result.getContent().stream().map(RateHistoryResponse::rate).toList());
		verify(rateHistoryRepository).findHistory(eq(eurUsd.getId()), any(), any(), any());
	}

	@Test
	void historyOfAUsdBasedCrossPairReadsOnlyTheQuoteLeg() {
		AssetPair eurUsd = givenPair(eur, usd, "1", FRESH);
		Instant t1 = Instant.parse("2026-10-12T10:00:00Z");
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any())).thenReturn(new PageImpl<>(
				List.of(history(eurUsd, "1.25", t1)), pageable, 1));

		Page<RateHistoryResponse> result = service.getHistory("USD", "EUR", null, null, pageable);

		assertEquals(List.of(new BigDecimal("0.80")),
				result.getContent().stream().map(RateHistoryResponse::rate).toList());
		verify(rateHistoryRepository, never()).findBetweenAscending(any(), any(), any());
	}

	@Test
	void missingFxOrCryptoRateIsRateNotAvailableNotZero() {
		AssetPair btcUsd = pair(btc, usd);
		AssetPair usdInr = pair(usd, inr);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "BTC", "USD")).thenReturn(Optional.of(btcUsd));
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR")).thenReturn(Optional.of(usdInr));
		when(exchangeRateRepository.findByPairIdWithAssets(btcUsd.getId()))
				.thenReturn(Optional.of(rate(btcUsd, "100000", FRESH)));

		assertThrows(RateNotAvailableException.class, () -> service.getCurrentRate("BTC", "INR"));

		when(exchangeRateRepository.findByPairIdWithAssets(btcUsd.getId())).thenReturn(Optional.empty());
		when(exchangeRateRepository.findByPairIdWithAssets(usdInr.getId()))
				.thenReturn(Optional.of(rate(usdInr, "90", FRESH)));
		assertThrows(RateNotAvailableException.class, () -> service.getCurrentRate("BTC", "INR"));
	}

	@Test
	void aNonPositiveStoredFxRateIsRateNotAvailable() {
		givenPair(btc, usd, "100000", FRESH);
		givenPair(usd, inr, "0", FRESH);

		assertThrows(RateNotAvailableException.class, () -> service.getCurrentRate("BTC", "INR"));
	}

	@Test
	void aStaleFxRateMarksTheConvertedPriceStaleWithTheOlderTimestamp() {
		Instant oldFx = NOW.minus(Duration.ofHours(3));
		givenPair(btc, usd, "100000", FRESH);
		givenPair(usd, inr, "90", oldFx);

		RateResponse response = service.getCurrentRate("BTC", "INR");

		assertTrue(response.stale());
		assertEquals(oldFx, response.observedAt());
		assertEquals(new BigDecimal("9000000.00"), response.rate());
	}

	@Test
	void aStaleCryptoRateAlsoMarksTheConvertedPriceStale() {
		givenPair(btc, usd, "100000", NOW.minus(Duration.ofHours(1)));
		givenPair(usd, inr, "90", FRESH);

		assertTrue(service.getCurrentRate("BTC", "INR").stale());
	}

	@Test
	void historyConvertsEachObservationWithTheFxRateInForceAtThatTime() {
		AssetPair btcUsd = givenPair(btc, usd, "1", FRESH);
		AssetPair usdInr = givenPair(usd, inr, "1", FRESH);
		Instant t1 = Instant.parse("2026-10-12T10:00:00Z");
		Instant t2 = Instant.parse("2026-10-12T10:05:00Z");
		Instant t3 = Instant.parse("2026-10-12T10:10:00Z");
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any())).thenReturn(new PageImpl<>(
				List.of(history(btcUsd, "100000", t3), history(btcUsd, "99000", t2), history(btcUsd, "98000", t1)),
				pageable, 3));
		// FX: 80 before the page, 90 from 10:04 (between t1 and t2), 100 from 10:10 (exactly t3).
		when(rateHistoryRepository.findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
				usdInr.getId(), t1)).thenReturn(Optional.of(history(usdInr, "80", t1.minusSeconds(600))));
		when(rateHistoryRepository.findBetweenAscending(usdInr.getId(), t1, t3)).thenReturn(List.of(
				history(usdInr, "90", Instant.parse("2026-10-12T10:04:00Z")),
				history(usdInr, "100", t3)));

		Page<RateHistoryResponse> result = service.getHistory("BTC", "INR", t1.minusSeconds(60), NOW, pageable);

		assertEquals(3, result.getTotalElements());
		assertEquals(List.of(new BigDecimal("10000000.00"), new BigDecimal("8910000.00"), new BigDecimal("7840000.00")),
				result.getContent().stream().map(RateHistoryResponse::rate).toList());
		assertEquals(List.of(t3, t2, t1), result.getContent().stream().map(RateHistoryResponse::observedAt).toList());
	}

	@Test
	void historyLeavesOutObservationsOlderThanEveryStoredFxObservation() {
		AssetPair btcUsd = givenPair(btc, usd, "1", FRESH);
		AssetPair usdInr = givenPair(usd, inr, "1", FRESH);
		Instant early = Instant.parse("2026-10-12T09:00:00Z");
		Instant late = Instant.parse("2026-10-12T10:00:00Z");
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any())).thenReturn(new PageImpl<>(
				List.of(history(btcUsd, "100", late), history(btcUsd, "100", early)), pageable, 2));
		when(rateHistoryRepository.findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
				usdInr.getId(), early)).thenReturn(Optional.empty());
		when(rateHistoryRepository.findBetweenAscending(usdInr.getId(), early, late))
				.thenReturn(List.of(history(usdInr, "90", Instant.parse("2026-10-12T09:30:00Z"))));

		Page<RateHistoryResponse> result = service.getHistory("BTC", "INR", early.minusSeconds(1), NOW, pageable);

		assertEquals(1, result.getContent().size());
		assertEquals(late, result.getContent().get(0).observedAt());
		assertEquals(new BigDecimal("9000.00"), result.getContent().get(0).rate());
	}

	@Test
	void historyForAnUnsupportedFiatIsAnUnsupportedPairAndAnEmptyRangeNeedsNoFxLookups() {
		givenPair(btc, usd, "1", FRESH);
		givenPair(usd, inr, "1", FRESH);
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		assertThrows(UnsupportedPairException.class, () -> service.getHistory("BTC", "JPY", null, null, pageable));
		assertTrue(service.getHistory("BTC", "INR", null, null, pageable).isEmpty());
		verify(rateHistoryRepository, never()).findBetweenAscending(any(), any(), any());
	}

	@Test
	void directHistoryIsUnchangedAndNeverTouchesFxHistory() {
		AssetPair btcUsd = givenPair(btc, usd, "1", FRESH);
		PageRequest pageable = PageRequest.of(0, 10);
		when(rateHistoryRepository.findHistory(any(), any(), any(), any())).thenReturn(new PageImpl<>(
				List.of(history(btcUsd, "100000.123456789012", FRESH)), pageable, 1));

		Page<RateHistoryResponse> result = service.getHistory("BTC", "USD", null, null, pageable);

		assertEquals(new BigDecimal("100000.123456789012"), result.getContent().get(0).rate());
		assertEquals("COINGECKO", result.getContent().get(0).providerCode());
		verifyNoInteractions(exchangeRateRepository);
		verify(rateHistoryRepository, never()).findBetweenAscending(any(), any(), any());
	}

	private AssetPair givenPair(Asset base, Asset quote, String rate, Instant observedAt) {
		AssetPair pair = pair(base, quote);
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, base.getSymbol(), quote.getSymbol()))
				.thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findByPairIdWithAssets(pair.getId()))
				.thenReturn(Optional.of(rate(pair, rate, observedAt)));
		return pair;
	}

	private static AssetPair pair(Asset base, Asset quote) {
		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setBaseAsset(base);
		pair.setQuoteAsset(quote);
		pair.setSymbol(base.getSymbol() + "/" + quote.getSymbol());
		pair.setStatus(AssetStatus.ACTIVE);
		return pair;
	}

	private static Asset asset(String symbol, AssetType type, int scale) {
		Asset asset = new Asset();
		setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(symbol);
		asset.setAssetType(type);
		asset.setStatus(AssetStatus.ACTIVE);
		asset.setDecimalScale((short) scale);
		return asset;
	}

	private static String providerFor(AssetPair pair) {
		return pair.getBaseAsset().getAssetType() == AssetType.CRYPTO ? "COINGECKO" : "TWELVEDATA";
	}

	private static ExchangeRate rate(AssetPair pair, String value, Instant observedAt) {
		ExchangeRate rate = new ExchangeRate();
		rate.setPair(pair);
		setField(rate, "pairId", pair.getId());
		rate.setRate(new BigDecimal(value));
		rate.setObservedAt(observedAt);
		rate.setFetchedAt(observedAt.plusSeconds(5));
		rate.setProviderCode(providerFor(pair));
		return rate;
	}

	private static RateHistory history(AssetPair pair, String value, Instant observedAt) {
		RateHistory history = new RateHistory();
		setField(history, "id", UUID.randomUUID());
		history.setPair(pair);
		history.setRate(new BigDecimal(value));
		history.setObservedAt(observedAt);
		history.setFetchedAt(observedAt.plusSeconds(1));
		history.setProviderCode(providerFor(pair));
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
		throw new IllegalStateException("No field " + name);
	}

}
