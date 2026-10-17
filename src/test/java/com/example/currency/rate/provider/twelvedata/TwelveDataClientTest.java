package com.example.currency.rate.provider.twelvedata;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataClientTest {

	private static final String BASE_URL = "https://api.twelvedata.com";

	private RateProperties properties;
	private MutableClock clock;
	private MockRestServiceServer server;
	private List<Long> sleeps;
	private TwelveDataClient client;

	@BeforeEach
	void setUp() {
		properties = new RateProperties();
		properties.getTwelvedata().setApiKey("test-key");
		properties.getTwelvedata().setRateLimitBackoff(java.time.Duration.ofMinutes(2));
		clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
		sleeps = new ArrayList<>();

		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new TwelveDataClient(builder.build(), properties, clock, sleeps::add);
	}

	@Test
	void parsesPriceAndSendsKeyInHeaderNotUrl() {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=USD%2FINR"))
				.andExpect(method(GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "apikey test-key"))
				.andExpect(requestTo(not(containsString("test-key"))))
				.andRespond(withSuccess("{\"price\":\"83.25400\"}", MediaType.APPLICATION_JSON));

		ProviderQuote quote = client.fetchQuote("USD/INR");

		assertEquals(0, new BigDecimal("83.254").compareTo(quote.rate()));
		assertNull(quote.observedAt(), "the /price endpoint has no timestamp; the client must not invent one");
		assertNull(quote.observationId());
		server.verify();
	}

	@Test
	void roundsPricesBeyondTheColumnScale() {
		respondWithPrice("0.1234567890126");

		assertEquals(new BigDecimal("0.123456789013"), client.fetchQuote("EUR/USD").rate());
	}

	@Test
	void rejectsZeroNegativeNonNumericAndMissingPrices() {
		for (String body : List.of(
				"{\"price\":\"0\"}",
				"{\"price\":\"0.0000000000001\"}",
				"{\"price\":\"-1.5\"}",
				"{\"price\":\"abc\"}",
				"{\"price\":null}",
				"{\"price\":true}",
				"{\"price\":\"12345678901234567.5\"}",
				"{}",
				"[]",
				"not json",
				"")) {
			server.reset();
			server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
					.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
			clock.advanceSeconds(60);

			assertThrows(RateProviderException.class, () -> client.fetchQuote("EUR/USD"), "body: " + body);
		}
	}

	@Test
	void treatsProviderErrorPayloadAsFailureNotAPrice() {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=XXX%2FYYY"))
				.andRespond(withSuccess(
						"{\"code\":400,\"message\":\"symbol is invalid\",\"status\":\"error\"}",
						MediaType.APPLICATION_JSON));

		RateProviderException ex = assertThrows(RateProviderException.class, () -> client.fetchQuote("XXX/YYY"));

		assertFalse(ex instanceof ProviderRateLimitException);
		assertTrue(ex.getMessage().contains("400"));
	}

	@Test
	void treatsHttpErrorsAndTimeoutsAsFailures() {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThrows(RateProviderException.class, () -> client.fetchQuote("EUR/USD"));

		server.reset();
		clock.advanceSeconds(60);
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withException(new IOException("read timed out")));
		RateProviderException timeout = assertThrows(RateProviderException.class, () -> client.fetchQuote("EUR/USD"));
		assertFalse(timeout instanceof ProviderRateLimitException);
		assertFalse(timeout.getMessage().contains("test-key"));
	}

	@Test
	void http429SuspendsTheClientWithoutFurtherRequests() {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"code\":429,\"message\":\"You have run out of API credits for the current minute.\","
								+ "\"status\":\"error\"}"));

		ProviderRateLimitException first = assertThrows(
				ProviderRateLimitException.class, () -> client.fetchQuote("EUR/USD"));
		assertEquals(clock.instant().plusSeconds(120), first.getRetryAfter());

		// Still inside the back-off: refused locally. MockRestServiceServer fails the test on any second request.
		clock.advanceSeconds(60);
		assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("GBP/USD"));
		server.verify();

		// After the back-off the client calls the provider again.
		clock.advanceSeconds(61);
		server.reset();
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=GBP%2FUSD"))
				.andRespond(withSuccess("{\"price\":\"1.30\"}", MediaType.APPLICATION_JSON));
		assertEquals(0, new BigDecimal("1.30").compareTo(client.fetchQuote("GBP/USD").rate()));
	}

	@Test
	void payload429WithDailyMessageSuspendsUntilNextUtcMidnight() {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withSuccess(
						"{\"code\":429,\"message\":\"You have run out of API credits for the day.\","
								+ "\"status\":\"error\"}",
						MediaType.APPLICATION_JSON));

		ProviderRateLimitException ex = assertThrows(
				ProviderRateLimitException.class, () -> client.fetchQuote("EUR/USD"));

		assertEquals(Instant.parse("2026-10-18T00:00:00Z"), ex.getRetryAfter());
	}

	@Test
	void stopsAtTheLocalDailyLimitAndResetsNextDay() {
		properties.getTwelvedata().setDailyRequestLimit(2);
		server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withSuccess("{\"price\":\"1.1\"}", MediaType.APPLICATION_JSON));

		client.fetchQuote("EUR/USD");
		client.fetchQuote("EUR/USD");
		ProviderRateLimitException ex = assertThrows(
				ProviderRateLimitException.class, () -> client.fetchQuote("EUR/USD"));
		assertEquals(Instant.parse("2026-10-18T00:00:00Z"), ex.getRetryAfter());
		server.verify();

		clock.set(Instant.parse("2026-10-18T00:00:01Z"));
		server.reset();
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withSuccess("{\"price\":\"1.1\"}", MediaType.APPLICATION_JSON));
		client.fetchQuote("EUR/USD");
		server.verify();
	}

	@Test
	void pacesRequestsButNotTheFirst() {
		properties.getTwelvedata().setRequestsPerMinute(6);
		server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withSuccess("{\"price\":\"1.1\"}", MediaType.APPLICATION_JSON));

		client.fetchQuote("EUR/USD");
		assertTrue(sleeps.isEmpty());
		client.fetchQuote("EUR/USD");

		assertEquals(1, sleeps.size());
		assertTrue(sleeps.get(0) > 9_000 && sleeps.get(0) <= 10_000, "expected ~10s spacing, got " + sleeps);
	}

	@Test
	void refusesMultiSymbolAndBlankSymbolsWithoutSpendingCredits() {
		assertThrows(RateProviderException.class, () -> client.fetchQuote("EUR/USD,GBP/USD"));
		assertThrows(RateProviderException.class, () -> client.fetchQuote(" "));
		assertThrows(RateProviderException.class, () -> client.fetchQuote(null));
		server.verify();
	}

	@Test
	void reportsNotConfiguredWithoutAnApiKey() {
		properties.getTwelvedata().setApiKey(" ");

		assertFalse(client.isConfigured());
		assertThrows(RateProviderException.class, () -> client.fetchQuote("EUR/USD"));
		server.verify();
	}

	private void respondWithPrice(String price) {
		server.expect(once(), requestTo(BASE_URL + "/price?symbol=EUR%2FUSD"))
				.andRespond(withSuccess("{\"price\":\"" + price + "\"}", MediaType.APPLICATION_JSON));
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
