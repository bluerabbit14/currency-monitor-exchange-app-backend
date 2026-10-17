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
import com.example.currency.rate.provider.coingecko.CoinGeckoClient;
import com.example.currency.rate.repository.ExchangeRateRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoinGeckoCollectionServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
	private static final Instant PROVIDER_TIME = Instant.parse("2026-10-17T09:59:30Z");

	@Mock
	private AssetPairRepository assetPairRepository;

	@Mock
	private ExchangeRateRepository exchangeRateRepository;

	@Mock
	private RatePersistenceService ratePersistenceService;

	@Mock
	private CoinGeckoClient client;

	private CoinGeckoCollectionService service;

	@BeforeEach
	void setUp() {
		service = new CoinGeckoCollectionService(assetPairRepository, exchangeRateRepository,
				ratePersistenceService, client, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void fetchesAllMappedPairsInOneRequestAndRecordsEach() {
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		AssetPair eth = pair("ETH/USD", "ethereum", "usd");
		AssetPair btcEur = pair("BTC/EUR", "bitcoin", "eur");
		givenPairs(btc, eth, btcEur);
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection())).thenReturn(Map.of(
				"bitcoin:usd", new ProviderQuote(new BigDecimal("67000.5"), PROVIDER_TIME, null),
				"ethereum:usd", new ProviderQuote(new BigDecimal("2400"), PROVIDER_TIME, null),
				"bitcoin:eur", new ProviderQuote(new BigDecimal("61000"), PROVIDER_TIME, null)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(3, 0, 0, 0, 0, false), summary);
		ArgumentCaptor<Collection<String>> ids = captor();
		ArgumentCaptor<Collection<String>> currencies = captor();
		verify(client, times(1)).fetchPrices(ids.capture(), currencies.capture());
		assertEquals(java.util.Set.of("bitcoin", "ethereum"), java.util.Set.copyOf(ids.getValue()));
		assertEquals(java.util.Set.of("usd", "eur"), java.util.Set.copyOf(currencies.getValue()));

		ArgumentCaptor<RateObservation> observations = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService, times(3)).recordObservation(observations.capture());
		RateObservation first = observations.getAllValues().get(0);
		assertEquals(btc.getId(), first.pairId());
		assertEquals(new BigDecimal("67000.5"), first.rate());
		assertEquals(PROVIDER_TIME, first.observedAt());
		assertEquals(NOW, first.fetchedAt());
		assertEquals("COINGECKO", first.providerCode());
		assertEquals(null, first.providerObservationId());
	}

	@Test
	void usesFetchTimeWhenTheProviderSuppliesNoTimestamp() {
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		givenPairs(btc);
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection()))
				.thenReturn(Map.of("bitcoin:usd", new ProviderQuote(BigDecimal.TEN, null, null)));

		service.collect();

		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		assertEquals(NOW, captor.getValue().observedAt());
	}

	@Test
	void skipsPricesNotNewerThanTheStoredObservationSoHistoryHasNoRepeats() {
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		AssetPair eth = pair("ETH/USD", "ethereum", "usd");
		givenPairs(btc, eth);
		ExchangeRate stored = mock(ExchangeRate.class);
		when(stored.getPairId()).thenReturn(btc.getId());
		when(stored.getObservedAt()).thenReturn(PROVIDER_TIME);
		when(exchangeRateRepository.findAllById(any())).thenReturn(List.of(stored));
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection())).thenReturn(Map.of(
				"bitcoin:usd", new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null),
				"ethereum:usd", new ProviderQuote(BigDecimal.TEN, PROVIDER_TIME, null)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 1, 0, 0, false), summary);
		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		assertEquals(eth.getId(), captor.getValue().pairId());
	}

	@Test
	void missingPricesAreFailuresAndNeverRecorded() {
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		AssetPair eth = pair("ETH/USD", "ethereum", "usd");
		givenPairs(btc, eth);
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection()))
				.thenReturn(Map.of("bitcoin:usd", new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 1, 0, 0, 0, false), summary);
		ArgumentCaptor<RateObservation> captor = ArgumentCaptor.forClass(RateObservation.class);
		verify(ratePersistenceService).recordObservation(captor.capture());
		assertEquals(btc.getId(), captor.getValue().pairId());
	}

	@Test
	void onePairFailingToPersistDoesNotStopTheOthers() {
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		AssetPair eth = pair("ETH/USD", "ethereum", "usd");
		givenPairs(btc, eth);
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection())).thenReturn(Map.of(
				"bitcoin:usd", new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null),
				"ethereum:usd", new ProviderQuote(BigDecimal.TEN, PROVIDER_TIME, null)));
		doThrow(new InvalidRateObservationException("bad")).when(ratePersistenceService)
				.recordObservation(org.mockito.ArgumentMatchers.argThat(o -> o.pairId().equals(btc.getId())));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 1, 0, 0, 0, false), summary);
		verify(ratePersistenceService, times(2)).recordObservation(any());
	}

	@Test
	void onlyCollectsPairsWhoseMappingAgreesWithTheirAssets() {
		AssetPair good = pair("BTC/USD", "bitcoin", "usd");
		AssetPair wrongCoinId = pair("ETH/USD", "ethereum", "usd");
		wrongCoinId.getBaseAsset().setExternalId("ethereum-classic");
		AssetPair wrongQuote = pair("SOL/USD", "solana", "usd");
		wrongQuote.getQuoteAsset().setCurrencyCode("EUR");
		AssetPair tickerAsCoinId = pair("BNB/USD", "bnb", "usd");
		tickerAsCoinId.getBaseAsset().setExternalId("binancecoin");
		AssetPair cryptoQuote = pair("BTC/ETH", "bitcoin", "eth");
		cryptoQuote.getQuoteAsset().setAssetType(AssetType.CRYPTO);
		AssetPair inactiveBase = pair("DOT/USD", "polkadot", "usd");
		inactiveBase.getBaseAsset().setStatus(AssetStatus.INACTIVE);
		AssetPair otherProviderAsset = pair("ADA/USD", "cardano", "usd");
		otherProviderAsset.getBaseAsset().setProvider("OTHER");
		AssetPair malformedCode = pair("XRP/USD", "ripple", "usd");
		malformedCode.setProviderPairCode("ripple");
		AssetPair blankCode = pair("LTC/USD", "litecoin", "usd");
		blankCode.setProviderPairCode(" ");
		AssetPair duplicate = pair("BTC/USD2", "bitcoin", "usd");
		givenPairs(good, wrongCoinId, wrongQuote, tickerAsCoinId, cryptoQuote, inactiveBase, otherProviderAsset,
				malformedCode, blankCode, duplicate);
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection()))
				.thenReturn(Map.of("bitcoin:usd", new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(1, 0, 9, 0, 0, false), summary);
		ArgumentCaptor<Collection<String>> ids = captor();
		verify(client).fetchPrices(ids.capture(), anyCollection());
		assertEquals(List.of("bitcoin"), List.copyOf(ids.getValue()));
	}

	@Test
	void emptyPairConfigurationMakesNoRequest() {
		when(client.isConfigured()).thenReturn(true);
		when(assetPairRepository.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, "COINGECKO"))
				.thenReturn(List.of());

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(0, 0, 0, 0, 0, false), summary);
		verify(client, never()).fetchPrices(anyCollection(), anyCollection());
		verifyNoInteractions(ratePersistenceService);
	}

	@Test
	void unconfiguredClientMakesNoRequestAndTouchesNoData() {
		when(client.isConfigured()).thenReturn(false);

		assertEquals(new CollectionSummary(0, 0, 0, 0, 0, false), service.collect());

		verifyNoInteractions(assetPairRepository, ratePersistenceService);
		verify(client, never()).fetchPrices(anyCollection(), anyCollection());
	}

	@Test
	void rateLimitStopsTheRunWithoutRetryAndWritesNothing() {
		givenPairs(pair("BTC/USD", "bitcoin", "usd"), pair("ETH/USD", "ethereum", "usd"));
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection()))
				.thenThrow(new ProviderRateLimitException("limit", NOW.plusSeconds(900)));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(0, 0, 0, 1, 2, false), summary);
		verify(client, times(1)).fetchPrices(anyCollection(), anyCollection());
		verifyNoInteractions(ratePersistenceService);
	}

	@Test
	void providerFailureLeavesStoredRatesUntouched() {
		givenPairs(pair("BTC/USD", "bitcoin", "usd"), pair("ETH/USD", "ethereum", "usd"));
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection()))
				.thenThrow(new RateProviderException("CoinGecko error HTTP 503"));

		CollectionSummary summary = service.collect();

		assertEquals(new CollectionSummary(0, 2, 0, 0, 0, false), summary);
		verify(client, times(1)).fetchPrices(anyCollection(), anyCollection());
		verifyNoInteractions(ratePersistenceService);
	}

	@Test
	void overlappingRunReturnsImmediatelyWithoutASecondRequest() throws Exception {
		CountDownLatch inFetch = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		givenPairs(pair("BTC/USD", "bitcoin", "usd"));
		when(client.isConfigured()).thenReturn(true);
		when(client.fetchPrices(anyCollection(), anyCollection())).thenAnswer(invocation -> {
			inFetch.countDown();
			assertTrue(release.await(5, TimeUnit.SECONDS));
			return Map.of("bitcoin:usd", new ProviderQuote(BigDecimal.ONE, PROVIDER_TIME, null));
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<CollectionSummary> first = executor.submit(service::collect);
			assertTrue(inFetch.await(5, TimeUnit.SECONDS));

			CollectionSummary second = service.collect();

			assertTrue(second.overlapped());
			release.countDown();
			assertEquals(1, first.get(5, TimeUnit.SECONDS).succeeded());
			verify(client, times(1)).fetchPrices(anyCollection(), anyCollection());
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void theTwelveDataCollectorStillIgnoresCoinGeckoPairs() {
		RateProvider twelveData = mock(RateProvider.class);
		when(twelveData.code()).thenReturn("TWELVEDATA");
		RateCollectionService twelveDataService = new RateCollectionService(
				assetPairRepository, ratePersistenceService, List.of(twelveData), Clock.fixed(NOW, ZoneOffset.UTC));
		AssetPair btc = pair("BTC/USD", "bitcoin", "usd");
		AssetPair eurUsd = new AssetPair();
		setField(eurUsd, "id", UUID.randomUUID());
		eurUsd.setSymbol("EUR/USD");
		eurUsd.setProviderCode("TWELVEDATA");
		eurUsd.setProviderPairCode("EUR/USD");
		when(assetPairRepository.findByStatus(AssetStatus.ACTIVE)).thenReturn(List.of(btc, eurUsd));
		when(twelveData.isConfigured()).thenReturn(true);
		when(twelveData.fetchQuote("EUR/USD")).thenReturn(new ProviderQuote(new BigDecimal("1.1"), null, null));

		CollectionSummary summary = twelveDataService.collect();

		assertEquals(new CollectionSummary(1, 0, 1, 0, 0, false), summary);
		verify(twelveData, times(1)).fetchQuote(any());
		verifyNoInteractions(client);
	}

	private void givenPairs(AssetPair... pairs) {
		when(assetPairRepository.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, "COINGECKO"))
				.thenReturn(List.of(pairs));
	}

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<Collection<String>> captor() {
		return ArgumentCaptor.forClass((Class<Collection<String>>) (Class<?>) Collection.class);
	}

	private static AssetPair pair(String symbol, String coinId, String currency) {
		Asset base = new Asset();
		base.setSymbol(symbol.substring(0, symbol.indexOf('/')));
		base.setAssetType(AssetType.CRYPTO);
		base.setStatus(AssetStatus.ACTIVE);
		base.setProvider("COINGECKO");
		base.setExternalId(coinId);

		Asset quote = new Asset();
		quote.setSymbol(currency.toUpperCase());
		quote.setAssetType(AssetType.FIAT);
		quote.setStatus(AssetStatus.ACTIVE);
		quote.setCurrencyCode(currency.toUpperCase());

		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setSymbol(symbol);
		pair.setBaseAsset(base);
		pair.setQuoteAsset(quote);
		pair.setStatus(AssetStatus.ACTIVE);
		pair.setProviderCode("COINGECKO");
		pair.setProviderPairCode(coinId + ":" + currency);
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
