package com.example.currency.rate.provider.coingecko;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinGeckoClientTest {

	private static final String BASE_URL = "https://api.coingecko.com/api/v3";
	private static final String PATH = BASE_URL + "/simple/price";

	private RateProperties properties;
	private MutableClock clock;
	private MockRestServiceServer server;
	private CoinGeckoClient client;

	@BeforeEach
	void setUp() {
		properties = new RateProperties();
		properties.getCoingecko().setApiKey("test-key");
		clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));

		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new CoinGeckoClient(builder.build(), properties, clock);
	}

	@Test
	void buildsOneBatchedRequestWithSortedIdsAndKeyInHeaderNotUrl() {
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andExpect(method(GET))
				.andExpect(queryParam("ids", "bitcoin%2Cethereum%2Csolana"))
				.andExpect(queryParam("vs_currencies", "eur%2Cusd"))
				.andExpect(queryParam("include_last_updated_at", "true"))
				.andExpect(header("x-cg-demo-api-key", "test-key"))
				.andExpect(requestTo(not(containsString("test-key"))))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		// Unsorted, mixed case, duplicated and blank inputs collapse into one normalised request.
		client.fetchPrices(List.of("solana", "Bitcoin", "ethereum", "bitcoin", " "), List.of("USD", "eur"));

		server.verify();
	}

	@Test
	void usesTheConfiguredApiKeyHeader() {
		properties.getCoingecko().setApiKeyHeader("x-cg-pro-api-key");
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andExpect(header("x-cg-pro-api-key", "test-key"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.fetchPrices(List.of("bitcoin"), List.of("usd"));

		server.verify();
	}

	@Test
	void mapsEachCoinAndCurrencyToAKeyedQuoteWithProviderTime() {
		respond("""
				{"bitcoin":{"usd":67187.33,"last_updated_at":1760695200},
				 "ethereum":{"usd":2441.5,"last_updated_at":1760695230},
				 "dogecoin":{"usd":0.2}}
				""");

		Map<String, ProviderQuote> quotes = client.fetchPrices(List.of("bitcoin", "ethereum"), List.of("usd"));

		assertEquals(2, quotes.size(), "coins that were not requested must be ignored");
		ProviderQuote bitcoin = quotes.get("bitcoin:usd");
		assertEquals(0, new BigDecimal("67187.33").compareTo(bitcoin.rate()));
		assertEquals(Instant.ofEpochSecond(1760695200L), bitcoin.observedAt());
		assertNull(bitcoin.observationId(), "CoinGecko supplies no observation id and none may be invented");
		assertEquals(Instant.ofEpochSecond(1760695230L), quotes.get("ethereum:usd").observedAt());
	}

	@Test
	void keepsSmallPricesExactlyAndRoundsBeyondTheColumnScale() {
		respond("{\"bitcoin\":{\"usd\":0.00000123},\"ethereum\":{\"usd\":0.1234567890126}}");

		Map<String, ProviderQuote> quotes = client.fetchPrices(List.of("bitcoin", "ethereum"), List.of("usd"));

		assertEquals(0, new BigDecimal("0.00000123").compareTo(quotes.get("bitcoin:usd").rate()));
		assertEquals(new BigDecimal("0.123456789013"), quotes.get("ethereum:usd").rate());
		assertNull(quotes.get("bitcoin:usd").observedAt(), "no last_updated_at: the client must not invent a time");
	}

	@Test
	void ignoresInvalidLastUpdatedAtButKeepsThePrice() {
		respond("{\"bitcoin\":{\"usd\":10,\"last_updated_at\":\"soon\"},\"ethereum\":{\"usd\":10,\"last_updated_at\":-5}}");

		Map<String, ProviderQuote> quotes = client.fetchPrices(List.of("bitcoin", "ethereum"), List.of("usd"));

		assertNull(quotes.get("bitcoin:usd").observedAt());
		assertNull(quotes.get("ethereum:usd").observedAt());
	}

	@Test
	void returnsOnlyTheCoinsPresentInAPartialResponse() {
		respond("{\"bitcoin\":{\"usd\":100,\"last_updated_at\":1760695200}}");

		Map<String, ProviderQuote> quotes = client.fetchPrices(List.of("bitcoin", "ethereum"), List.of("usd"));

		assertEquals(List.of("bitcoin:usd"), List.copyOf(quotes.keySet()));
	}

	@Test
	void dropsZeroNegativeNonNumericAndMissingPrices() {
		respond("""
				{"a":{"usd":0},"b":{"usd":-1.5},"c":{"usd":"12"},"d":{"usd":null},"e":{"usd":true},
				 "f":{"eur":5},"g":"oops","h":{"usd":12345678901234567.5},"i":{"usd":0.0000000000001},
				 "ok":{"usd":2}}
				""");

		Map<String, ProviderQuote> quotes = client.fetchPrices(
				List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "ok"), List.of("usd"));

		assertEquals(List.of("ok:usd"), List.copyOf(quotes.keySet()));
	}

	@Test
	void rejectsMalformedAndUnexpectedBodies() {
		for (String body : List.of("not json", "", "[]", "\"x\"", "null", "{\"bitcoin\":")) {
			server.reset();
			respond(body);
			clock.advanceSeconds(1);

			assertThrows(RateProviderException.class,
					() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")), "body: " + body);
		}
	}

	@Test
	void http429SuspendsUsingRetryAfterWithoutRetrying() {
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "90"));

		ProviderRateLimitException first = assertThrows(ProviderRateLimitException.class,
				() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		assertEquals(clock.instant().plusSeconds(90), first.getRetryAfter());

		// Inside the suspension: refused locally. MockRestServiceServer fails the test on a second request.
		clock.advanceSeconds(60);
		assertThrows(ProviderRateLimitException.class, () -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		server.verify();

		clock.advanceSeconds(31);
		server.reset();
		respond("{\"bitcoin\":{\"usd\":1}}");
		assertEquals(1, client.fetchPrices(List.of("bitcoin"), List.of("usd")).size());
	}

	@Test
	void http429WithoutUsableRetryAfterUsesTheConfiguredBackoff() {
		properties.getCoingecko().setRateLimitBackoff(Duration.ofMinutes(15));
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.header(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT"));

		ProviderRateLimitException ex = assertThrows(ProviderRateLimitException.class,
				() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));

		assertEquals(clock.instant().plus(Duration.ofMinutes(15)), ex.getRetryAfter());
	}

	@Test
	void treatsServerErrorsAndTimeoutsAsPlainFailuresWithoutLeakingTheKey() {
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("key=test-key"));
		RateProviderException serverError = assertThrows(RateProviderException.class,
				() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		assertFalse(serverError instanceof ProviderRateLimitException);
		assertTrue(serverError.getMessage().contains("503"));
		assertFalse(serverError.getMessage().contains("test-key"));

		server.reset();
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withException(new IOException("read timed out for " + PATH + "?x-cg-demo-api-key=test-key")));
		RateProviderException timeout = assertThrows(RateProviderException.class,
				() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		assertFalse(timeout instanceof ProviderRateLimitException);
		assertFalse(timeout.getMessage().contains("test-key"));
	}

	@Test
	void stopsAtTheLocalMonthlyLimitAndResetsNextMonth() {
		properties.getCoingecko().setMonthlyRequestLimit(2);
		server.expect(times(2), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.fetchPrices(List.of("bitcoin"), List.of("usd"));
		client.fetchPrices(List.of("bitcoin"), List.of("usd"));
		ProviderRateLimitException ex = assertThrows(ProviderRateLimitException.class,
				() -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		assertEquals(Instant.parse("2026-11-01T00:00:00Z"), ex.getRetryAfter());
		server.verify();

		clock.set(Instant.parse("2026-11-01T00:00:01Z"));
		server.reset();
		respond("{}");
		client.fetchPrices(List.of("bitcoin"), List.of("usd"));
		server.verify();
	}

	@Test
	void refusesToCallWithoutAKeyOrWithNothingToFetch() {
		assertThrows(RateProviderException.class, () -> client.fetchPrices(List.of(), List.of("usd")));
		assertThrows(RateProviderException.class, () -> client.fetchPrices(List.of("bitcoin"), List.of()));
		assertThrows(RateProviderException.class, () -> client.fetchPrices(null, null));

		properties.getCoingecko().setApiKey(" ");
		assertFalse(client.isConfigured());
		assertThrows(RateProviderException.class, () -> client.fetchPrices(List.of("bitcoin"), List.of("usd")));
		server.verify();
	}

	private void respond(String body) {
		server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(PATH)))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advanceSeconds(long seconds) {
			now = now.plusSeconds(seconds);
		}

		void set(Instant instant) {
			now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
