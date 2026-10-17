package com.example.currency.rate.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.provider.coingecko.CoinGeckoClient;
import com.example.currency.rate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The ten supported cryptocurrencies end to end through the real client (mocked HTTP) and the real service
 * (mocked persistence), plus a check of the V16 catalog migration text.
 */
class CoinGeckoSupportedCoinsTest {

	private static final Map<String, String> COINS = new LinkedHashMap<>();

	static {
		COINS.put("BTC", "bitcoin");
		COINS.put("ETH", "ethereum");
		COINS.put("USDT", "tether");
		COINS.put("BNB", "binancecoin");
		COINS.put("SOL", "solana");
		COINS.put("XRP", "ripple");
		COINS.put("USDC", "usd-coin");
		COINS.put("DOGE", "dogecoin");
		COINS.put("ADA", "cardano");
		COINS.put("TRX", "tron");
	}

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

	@Test
	void requestsAllTenCoinsInOneUsdRequestAndPersistsEachPrice() {
		RateProperties properties = new RateProperties();
		properties.getCoingecko().setApiKey("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.coingecko.com/api/v3");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		CoinGeckoClient client = new CoinGeckoClient(builder.build(), properties, clock);

		AssetPairRepository pairs = mock(AssetPairRepository.class);
		ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
		RatePersistenceService persistence = mock(RatePersistenceService.class);
		List<AssetPair> catalog = new ArrayList<>();
		COINS.forEach((symbol, coinId) -> catalog.add(pair(symbol, coinId)));
		when(pairs.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, "COINGECKO")).thenReturn(catalog);

		StringBuilder body = new StringBuilder("{");
		int i = 0;
		for (String coinId : COINS.values()) {
			body.append(i++ == 0 ? "" : ",").append('"').append(coinId).append("\":{\"usd\":")
					.append(i).append(".5,\"last_updated_at\":1760695200}");
		}
		body.append('}');
		// Exactly one HTTP request is allowed: a second one fails the mock server.
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.coingecko.com/api/v3/simple/price")))
				.andExpect(queryParam("ids",
						"binancecoin%2Cbitcoin%2Ccardano%2Cdogecoin%2Cethereum%2Cripple%2Csolana%2Ctether%2Ctron%2Cusd-coin"))
				.andExpect(queryParam("vs_currencies", "usd"))
				.andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

		CoinGeckoCollectionService service =
				new CoinGeckoCollectionService(pairs, rates, persistence, client, clock);

		CollectionSummary summary = service.collect();

		server.verify();
		assertEquals(new CollectionSummary(10, 0, 0, 0, 0, false), summary);
		verify(persistence, times(10)).recordObservation(argThat(o ->
				"COINGECKO".equals(o.providerCode()) && o.rate().signum() > 0
						&& o.observedAt().equals(Instant.ofEpochSecond(1760695200L))));
		verify(rates).findAllById(any());
	}

	@Test
	void stablecoinsKeepTheirMarketPriceThroughTheClient() {
		RateProperties properties = new RateProperties();
		properties.getCoingecko().setApiKey("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.coingecko.com/api/v3");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		CoinGeckoClient client = new CoinGeckoClient(builder.build(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.coingecko.com/api/v3/simple/price")))
				.andRespond(withSuccess("{\"tether\":{\"usd\":0.99931},\"usd-coin\":{\"usd\":1.0002}}",
						MediaType.APPLICATION_JSON));

		var quotes = client.fetchPrices(List.of("tether", "usd-coin"), List.of("usd"));

		assertEquals(0, new BigDecimal("0.99931").compareTo(quotes.get("tether:usd").rate()));
		assertEquals(0, new BigDecimal("1.0002").compareTo(quotes.get("usd-coin:usd").rate()));
	}

	@Test
	void migrationMapsEveryCoinAndCreatesTheUsdPairsIdempotently() throws IOException {
		String sql;
		try (InputStream in = getClass().getResourceAsStream("/db/migration/V16__seed_top_crypto_usd_pairs.sql")) {
			sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		Matcher matcher = Pattern.compile("\\('([A-Z]+)', (?:'[^']*', )?'([a-z-]+)'\\)").matcher(sql);
		Map<String, String> found = new LinkedHashMap<>();
		while (matcher.find()) {
			found.put(matcher.group(1), matcher.group(2));
		}
		assertEquals(COINS, found);
		assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol)"),
				"assets must be reused, never duplicated");
		assertTrue(sql.contains("NOT EXISTS ("), "pairs must be reused, never duplicated");
		assertTrue(sql.contains("base.external_id || ':usd'"));
		assertTrue(sql.contains("usd.symbol = 'USD' AND usd.asset_type = 'FIAT'"), "the existing USD asset is reused");
	}

	private static AssetPair pair(String symbol, String coinId) {
		Asset base = new Asset();
		base.setSymbol(symbol);
		base.setAssetType(AssetType.CRYPTO);
		base.setStatus(AssetStatus.ACTIVE);
		base.setProvider("COINGECKO");
		base.setExternalId(coinId);
		Asset usd = new Asset();
		usd.setSymbol("USD");
		usd.setAssetType(AssetType.FIAT);
		usd.setStatus(AssetStatus.ACTIVE);
		usd.setCurrencyCode("USD");
		AssetPair pair = new AssetPair();
		try {
			var field = AssetPair.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(pair, UUID.randomUUID());
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		pair.setBaseAsset(base);
		pair.setQuoteAsset(usd);
		pair.setSymbol(symbol + "/USD");
		pair.setStatus(AssetStatus.ACTIVE);
		pair.setProviderCode("COINGECKO");
		pair.setProviderPairCode(coinId + ":usd");
		return pair;
	}

}
