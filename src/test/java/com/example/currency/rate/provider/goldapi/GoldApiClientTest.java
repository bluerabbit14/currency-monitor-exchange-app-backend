package com.example.currency.rate.provider.goldapi;

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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoldApiClientTest {

	private static final String BASE_URL = "https://api.gold-api.com";

	private RateProperties properties;
	private MutableClock clock;
	private MockRestServiceServer server;
	private List<Long> sleeps;
	private GoldApiClient client;

	@BeforeEach
	void setUp() {
		properties = new RateProperties();
		properties.getGoldapi().setRequestsPerMinute(6);
		clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
		sleeps = new ArrayList<>();
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GoldApiClient(builder.build(), properties, clock, sleeps::add);
	}

	@Test
	void requestsOneSymbolWithoutAnyCredentialsAndParsesPriceAndProviderTime() {
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andExpect(method(GET))
				.andExpect(request -> assertFalse(request.getHeaders().containsHeader("x-api-key")))
				.andExpect(request -> assertFalse(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)))
				.andRespond(withSuccess(body("XAU", "USD", "4165.200195", "2026-07-03T16:08:54Z"),
						MediaType.APPLICATION_JSON));

		ProviderQuote quote = client.fetchQuote("xau");

		assertEquals(0, new BigDecimal("4165.200195").compareTo(quote.rate()));
		assertEquals(Instant.parse("2026-07-03T16:08:54Z"), quote.observedAt());
		assertNull(quote.observationId(), "Gold API supplies no observation id and none may be invented");
		server.verify();
	}

	@Test
	void keepsTheFetchTimeFallbackWhenUpdatedAtIsMissingOrUnreadable() {
		respond("XAG", "{\"symbol\":\"XAG\",\"currency\":\"USD\",\"price\":30.5}");
		assertNull(client.fetchQuote("XAG").observedAt());

		server.reset();
		clock.advanceSeconds(60);
		respond("XAG", body("XAG", "USD", "30.5", "yesterday-ish"));
		assertNull(client.fetchQuote("XAG").observedAt());
	}

	@Test
	void roundsBeyondTheColumnScaleAndKeepsSmallPrices() {
		respond("XPT", body("XPT", "USD", "0.1234567890126", "2026-10-17T09:59:00Z"));

		assertEquals(new BigDecimal("0.123456789013"), client.fetchQuote("XPT").rate());
	}

	@Test
	void rejectsWrongQuoteCurrencyWrongSymbolAndBadPrices() {
		List<String> bodies = List.of(
				body("XAU", "EUR", "3900", "2026-10-17T09:59:00Z"),
				"{\"symbol\":\"XAU\",\"price\":3900}",
				body("XAG", "USD", "3900", "2026-10-17T09:59:00Z"),
				body("XAU", "USD", "0", "2026-10-17T09:59:00Z"),
				body("XAU", "USD", "-5", "2026-10-17T09:59:00Z"),
				"{\"symbol\":\"XAU\",\"currency\":\"USD\",\"price\":\"3900\"}",
				"{\"symbol\":\"XAU\",\"currency\":\"USD\",\"price\":null}",
				"{\"symbol\":\"XAU\",\"currency\":\"USD\"}",
				"{\"symbol\":\"XAU\",\"currency\":\"USD\",\"price\":12345678901234567.5}",
				"{}", "[]", "not json", "");
		for (String bad : bodies) {
			server.reset();
			clock.advanceSeconds(60);
			respond("XAU", bad);

			assertThrows(RateProviderException.class, () -> client.fetchQuote("XAU"), "body: " + bad);
		}
	}

	@Test
	void treatsServerErrorsAndTimeoutsAsPlainFailures() {
		server.expect(once(), requestTo(BASE_URL + "/price/XAU")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		RateProviderException serverError = assertThrows(RateProviderException.class, () -> client.fetchQuote("XAU"));
		assertFalse(serverError instanceof ProviderRateLimitException);
		assertTrue(serverError.getMessage().contains("502"));

		server.reset();
		clock.advanceSeconds(60);
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andRespond(withException(new IOException("read timed out")));
		RateProviderException timeout = assertThrows(RateProviderException.class, () -> client.fetchQuote("XAU"));
		assertFalse(timeout instanceof ProviderRateLimitException);
	}

	@Test
	void http429SuspendsUsingRetryAfterAndDoesNotRetry() {
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "90"));

		ProviderRateLimitException first = assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("XAU"));
		assertEquals(clock.instant().plusSeconds(90), first.getRetryAfter());

		// Inside the suspension every symbol is refused locally; a second HTTP call would fail the mock server.
		clock.advanceSeconds(60);
		assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("XAG"));
		server.verify();

		clock.advanceSeconds(31);
		server.reset();
		respond("XAG", body("XAG", "USD", "30", "2026-10-17T10:01:00Z"));
		assertEquals(0, new BigDecimal("30").compareTo(client.fetchQuote("XAG").rate()));
	}

	@Test
	void http429WithoutUsableRetryAfterUsesTheConfiguredBackoff() {
		properties.getGoldapi().setRateLimitBackoff(Duration.ofMinutes(2));
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "soon"));

		ProviderRateLimitException ex = assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("XAU"));

		assertEquals(clock.instant().plus(Duration.ofMinutes(2)), ex.getRetryAfter());
	}

	@Test
	void spacesRequestsToTheConfiguredRateAndNeverDelaysTheFirst() {
		properties.getGoldapi().setRequestsPerMinute(10);
		server.expect(times(4), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/price/")))
				.andRespond(withSuccess(body("XAU", "USD", "1", "2026-10-17T09:59:00Z"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			client.fetchQuote("XAU");
		}

		// The clock does not move in this test, so each request waits for the next free 6 s slot (10 per minute).
		assertEquals(List.of(6_000L, 12_000L, 18_000L), sleeps);
	}

	@Test
	void fiveCommoditiesStayBelowTenRequestsPerMinuteAtTheDefaultRate() {
		server.expect(times(5), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/price/")))
				.andRespond(withSuccess(body("XAU", "USD", "1", "2026-10-17T09:59:00Z"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 5; i++) {
			client.fetchQuote("XAU");
			// Model real time: the sleeper waits, then the clock has moved to the slot that was reserved.
			if (!sleeps.isEmpty()) {
				clock.set(clock.instant().plusMillis(sleeps.get(sleeps.size() - 1)));
			}
		}

		// 6 per minute = one request every 10 s, so five requests span at least 40 s: never more than 6 in a minute.
		assertTrue(properties.getGoldapi().getRequestsPerMinute() < 10);
		assertEquals(4, sleeps.size());
	}


	@Test
	void sendsTheApiKeyInAHeaderOnlyAndOnlyWhenConfigured() {
		properties.getGoldapi().setApiKey("secret-key");
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("x-api-key", "secret-key"))
				.andRespond(withSuccess(body("XAU", "USD", "1", "2026-10-17T09:59:00Z"), MediaType.APPLICATION_JSON));
		client.fetchQuote("XAU");
		server.verify();

		server.reset();
		clock.advanceSeconds(60);
		properties.getGoldapi().setApiKey(" ");
		server.expect(once(), requestTo(BASE_URL + "/price/XAU"))
				.andExpect(request -> assertFalse(request.getHeaders().containsHeader("x-api-key")))
				.andRespond(withSuccess(body("XAU", "USD", "1", "2026-10-17T09:59:00Z"), MediaType.APPLICATION_JSON));
		client.fetchQuote("XAU");
		server.verify();
	}

	@Test
	void capsRequestsAtTenInAnyRollingHourWithoutCallingTheProviderAgain() {
		properties.getGoldapi().setRequestsPerHour(10);
		properties.getGoldapi().setRequestsPerMinute(0);
		server.expect(times(10), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/price/")))
				.andRespond(withSuccess(body("XAU", "USD", "1", "2026-10-17T09:59:00Z"), MediaType.APPLICATION_JSON));

		assertEquals(10, client.remainingRequests());
		for (int i = 0; i < 10; i++) {
			client.fetchQuote("XAU");
			clock.advanceSeconds(60);
		}

		assertEquals(0, client.remainingRequests());
		// Ten requests at t+0..t+9 min; the first leaves the window at t+60 min. An eleventh is refused locally
		// (the mock server would fail the test on a real eleventh HTTP call).
		ProviderRateLimitException ex = assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("XAG"));
		assertEquals(Instant.parse("2026-10-17T11:00:00Z"), ex.getRetryAfter());
		server.verify();

		clock.set(Instant.parse("2026-10-17T11:00:01Z"));
		assertEquals(1, client.remainingRequests());
	}

	@Test
	void aSuspensionAfter429ReportsNoRemainingRequests() {
		server.expect(once(), requestTo(BASE_URL + "/price/XAU")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThrows(ProviderRateLimitException.class, () -> client.fetchQuote("XAU"));

		assertEquals(0, client.remainingRequests());
		clock.advanceSeconds(121);
		assertTrue(client.remainingRequests() > 0);
	}
	@Test
	void refusesInvalidSymbolsWithoutARequest() {
		assertThrows(RateProviderException.class, () -> client.fetchQuote(null));
		assertThrows(RateProviderException.class, () -> client.fetchQuote(" "));
		assertThrows(RateProviderException.class, () -> client.fetchQuote("XAU/EUR"));
		assertThrows(RateProviderException.class, () -> client.fetchQuote("../history"));
		server.verify();
	}

	private void respond(String symbol, String body) {
		server.expect(once(), requestTo(BASE_URL + "/price/" + symbol))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static String body(String symbol, String currency, String price, String updatedAt) {
		return "{\"currency\":\"" + currency + "\",\"currencySymbol\":\"$\",\"exchangeRate\":1,\"name\":\"n\","
				+ "\"price\":" + price + ",\"symbol\":\"" + symbol + "\",\"updatedAt\":\"" + updatedAt
				+ "\",\"updatedAtReadable\":\"a few seconds ago\"}";
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
