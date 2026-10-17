package com.example.currency.rate.provider.twelvedata;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProvider;
import com.example.currency.rate.provider.RateProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * Twelve Data {@code GET /price?symbol=BASE/QUOTE}: one symbol per request, one credit per request.
 * The response carries only {@code price} (a string); it has no timestamp or observation id, so
 * {@link ProviderQuote#observedAt()} is always null and the collector applies its documented fallback.
 * <p>
 * The client also owns this provider's quota safety: request pacing, a local daily credit guard, and a
 * suspension after a rate-limit response. The API key travels in the Authorization header, never in the URL.
 */
@Component
public class TwelveDataClient implements RateProvider {

	public static final String CODE = "TWELVEDATA";

	private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);

	/** Highest integer-digit count the NUMERIC(28, 12) columns can hold. */
	private static final int MAX_INTEGER_DIGITS = 16;
	private static final int RATE_SCALE = 12;
	private static final int MAX_LOGGED_MESSAGE = 200;

	private final RestClient restClient;
	private final RateProperties.TwelveData settings;
	private final Clock clock;
	private final LongConsumer sleeper;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private long lastRequestNanos;
	private boolean requestedBefore;
	private LocalDate budgetDay;
	private int budgetUsed;
	private Instant suspendedUntil = Instant.MIN;

	@Autowired
	public TwelveDataClient(
			@Qualifier("twelveDataRestClient") RestClient restClient,
			RateProperties properties,
			Clock clock) {
		this(restClient, properties, clock, TwelveDataClient::sleepMillis);
	}

	TwelveDataClient(RestClient restClient, RateProperties properties, Clock clock, LongConsumer sleeper) {
		this.restClient = restClient;
		this.settings = properties.getTwelvedata();
		this.clock = clock;
		this.sleeper = sleeper;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public boolean isConfigured() {
		return settings.getApiKey() != null && !settings.getApiKey().isBlank();
	}

	@Override
	public ProviderQuote fetchQuote(String providerPairCode) {
		if (!isConfigured()) {
			throw new RateProviderException("Twelve Data API key is not configured");
		}
		if (providerPairCode == null || providerPairCode.isBlank() || providerPairCode.contains(",")) {
			// A comma would turn this into a multi-symbol request and spend several credits.
			throw new RateProviderException("Invalid Twelve Data symbol");
		}
		String symbol = providerPairCode.trim();

		reserveCredit();

		String body;
		try {
			body = restClient.get()
					.uri(uri -> uri.path("/price").queryParam("symbol", "{symbol}").build(symbol))
					.header(HttpHeaders.AUTHORIZATION, "apikey " + settings.getApiKey().trim())
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException ex) {
			throw failureFor(ex.getStatusCode().value(), ex.getResponseBodyAsString(), symbol);
		}
		catch (RestClientException ex) {
			// Timeouts and connection failures. The message is omitted: it can echo the request URL.
			throw new RateProviderException(
					"Twelve Data request for " + symbol + " failed: " + ex.getClass().getSimpleName(), ex);
		}
		return parseQuote(body, symbol);
	}

	private ProviderQuote parseQuote(String body, String symbol) {
		JsonNode root = readTree(body, symbol);

		boolean errorPayload = "error".equalsIgnoreCase(root.path("status").asString(""))
				|| (root.has("code") && !root.has("price"));
		if (errorPayload) {
			throw failureFor(root.path("code").asInt(0), body, symbol);
		}

		JsonNode price = root.get("price");
		if (price == null || !(price.isString() || price.isNumber())) {
			throw new RateProviderException("Twelve Data response for " + symbol + " has no price");
		}
		BigDecimal rate;
		try {
			rate = new BigDecimal(price.asString().trim());
		}
		catch (NumberFormatException ex) {
			throw new RateProviderException("Twelve Data price for " + symbol + " is not a number");
		}
		if (rate.compareTo(BigDecimal.ZERO) <= 0) {
			throw new RateProviderException("Twelve Data price for " + symbol + " is not positive");
		}
		if (rate.precision() - rate.scale() > MAX_INTEGER_DIGITS) {
			throw new RateProviderException("Twelve Data price for " + symbol + " is out of range");
		}
		if (rate.scale() > RATE_SCALE) {
			rate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
			if (rate.compareTo(BigDecimal.ZERO) <= 0) {
				throw new RateProviderException("Twelve Data price for " + symbol + " is not positive");
			}
		}
		return new ProviderQuote(rate, null, null);
	}

	private JsonNode readTree(String body, String symbol) {
		if (body == null || body.isBlank()) {
			throw new RateProviderException("Twelve Data returned an empty response for " + symbol);
		}
		try {
			JsonNode root = jsonMapper.readTree(body);
			if (root == null || !root.isObject()) {
				throw new RateProviderException("Twelve Data returned an unexpected response for " + symbol);
			}
			return root;
		}
		catch (JacksonException ex) {
			throw new RateProviderException("Twelve Data returned malformed JSON for " + symbol);
		}
	}

	/**
	 * Maps an HTTP status or payload error code to an exception. A 429 (HTTP or in the payload) suspends the
	 * client: until the next UTC midnight when the message says the daily credits are gone, otherwise for
	 * the configured back-off. The daily-versus-minute wording is read from the message, which Twelve Data
	 * does not document as a stable contract, so an unrecognised message falls back to the shorter back-off.
	 */
	private RateProviderException failureFor(int code, String body, String symbol) {
		String message = errorMessage(body);
		if (code == 429) {
			boolean daily = message.toLowerCase(Locale.ROOT).contains("for the day")
					|| message.toLowerCase(Locale.ROOT).contains("daily");
			Instant now = clock.instant();
			Instant until = daily ? nextUtcMidnight(now) : now.plus(settings.getRateLimitBackoff());
			synchronized (this) {
				suspendedUntil = until;
			}
			log.warn("Twelve Data rate limit hit ({}); suspending requests until {}",
					daily ? "daily credits" : "per-minute credits", until);
			return new ProviderRateLimitException("Twelve Data rate limit reached for " + symbol, until);
		}
		return new RateProviderException(
				"Twelve Data error " + code + " for " + symbol + ": " + message);
	}

	private String errorMessage(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonNode message = jsonMapper.readTree(body).path("message");
			String text = message.isString() ? message.asString() : "";
			return text.length() > MAX_LOGGED_MESSAGE ? text.substring(0, MAX_LOGGED_MESSAGE) : text;
		}
		catch (JacksonException ex) {
			return "";
		}
	}

	/**
	 * Counts one credit for the coming request, after the suspension and local-budget checks, then paces it.
	 * Synchronized so concurrent callers cannot exceed the per-minute spacing.
	 */
	private synchronized void reserveCredit() {
		Instant now = clock.instant();
		if (now.isBefore(suspendedUntil)) {
			throw new ProviderRateLimitException("Twelve Data requests are suspended after a rate limit", suspendedUntil);
		}
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		if (!today.equals(budgetDay)) {
			budgetDay = today;
			budgetUsed = 0;
		}
		if (budgetUsed >= settings.getDailyRequestLimit()) {
			throw new ProviderRateLimitException(
					"Twelve Data local daily request limit reached", nextUtcMidnight(now));
		}
		budgetUsed++;

		if (settings.getRequestsPerMinute() > 0) {
			long spacingNanos = 60_000_000_000L / settings.getRequestsPerMinute();
			long nowNanos = System.nanoTime();
			if (requestedBefore) {
				long waitNanos = lastRequestNanos + spacingNanos - nowNanos;
				if (waitNanos > 0) {
					sleeper.accept(Math.max(1, waitNanos / 1_000_000L));
				}
			}
			requestedBefore = true;
			lastRequestNanos = System.nanoTime();
		}
	}

	private static Instant nextUtcMidnight(Instant now) {
		return now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	private static void sleepMillis(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RateProviderException("Interrupted while pacing Twelve Data requests", ex);
		}
	}

}
