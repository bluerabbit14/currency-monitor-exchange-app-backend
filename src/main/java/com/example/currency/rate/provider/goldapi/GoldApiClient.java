package com.example.currency.rate.provider.goldapi;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

/**
 * Gold API {@code GET /price/{symbol}}: one commodity per request, USD by default, no authentication.
 * The response carries {@code price}, {@code currency}, {@code symbol} and an ISO-8601 {@code updatedAt};
 * the last becomes {@link ProviderQuote#observedAt()} (null when absent or unreadable, never invented).
 * There is no observation id.
 * <p>
 * Deliberately not a {@code RateProvider}: that would let the Twelve Data collector pick these pairs up.
 * <p>
 * The client owns this provider's request pacing and 429 handling. Requests are spaced to at most
 * {@code requestsPerMinute}, measured on the injected clock and counted from the previous request even across
 * runs, and the requests of the last hour are capped at {@code requestsPerHour}. An optional API key is sent in
 * the {@code x-api-key} header, never in the URL and never logged. A 429 suspends the client (for {@code Retry-After} seconds when given). It never retries. The limiter is
 * per application instance; it is not a global limit across instances.
 */
@Component
public class GoldApiClient {

	private static final Logger log = LoggerFactory.getLogger(GoldApiClient.class);

	private static final Pattern SYMBOL = Pattern.compile("^[A-Z]{2,6}$");
	/** Highest integer-digit count the NUMERIC(28, 12) columns can hold. */
	private static final int MAX_INTEGER_DIGITS = 16;
	private static final int RATE_SCALE = 12;
	private static final String QUOTE_CURRENCY = "USD";
	private static final Duration HOUR = Duration.ofHours(1);

	private final RestClient restClient;
	private final RateProperties.GoldApi settings;
	private final Clock clock;
	private final LongConsumer sleeper;
	private final JsonMapper jsonMapper = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.build();

	private Instant nextRequestAt = Instant.MIN;
	private Instant suspendedUntil = Instant.MIN;
	/** Start times of the requests made within the last hour, oldest first. */
	private final Deque<Instant> recentRequests = new ArrayDeque<>();

	@Autowired
	public GoldApiClient(@Qualifier("goldApiRestClient") RestClient restClient, RateProperties properties, Clock clock) {
		this(restClient, properties, clock, GoldApiClient::sleepMillis);
	}

	GoldApiClient(RestClient restClient, RateProperties properties, Clock clock, LongConsumer sleeper) {
		this.restClient = restClient;
		this.settings = properties.getGoldapi();
		this.clock = clock;
		this.sleeper = sleeper;
	}

	/**
	 * @param symbol the provider symbol, for example {@code XAU}
	 * @throws ProviderRateLimitException on HTTP 429, or while a suspension is in force
	 * @throws RateProviderException for any other failure or an unusable response
	 */
	public ProviderQuote fetchQuote(String symbol) {
		if (symbol == null || !SYMBOL.matcher(symbol.trim().toUpperCase(Locale.ROOT)).matches()) {
			throw new RateProviderException("Invalid Gold API symbol");
		}
		String code = symbol.trim().toUpperCase(Locale.ROOT);

		reserveRequest();

		String body;
		try {
			body = restClient.get()
					.uri(uri -> uri.path("/price/{symbol}").build(code))
					.headers(headers -> {
						if (hasApiKey()) {
							headers.set("x-api-key", settings.getApiKey().trim());
						}
					})
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException ex) {
			throw failureFor(ex.getStatusCode().value(), ex.getResponseHeaders(), code);
		}
		catch (RestClientException ex) {
			// Timeouts and connection failures. The message is omitted: it can echo the request URL.
			throw new RateProviderException(
					"Gold API request for " + code + " failed: " + ex.getClass().getSimpleName(), ex);
		}
		return parse(body, code);
	}

	private ProviderQuote parse(String body, String code) {
		JsonNode root = readTree(body, code);

		JsonNode symbol = root.get("symbol");
		if (symbol == null || !symbol.isString() || !code.equalsIgnoreCase(symbol.asString().trim())) {
			throw new RateProviderException("Gold API response for " + code + " is for a different symbol");
		}
		JsonNode currency = root.get("currency");
		if (currency == null || !currency.isString() || !QUOTE_CURRENCY.equalsIgnoreCase(currency.asString().trim())) {
			throw new RateProviderException("Gold API price for " + code + " is not quoted in " + QUOTE_CURRENCY);
		}
		JsonNode price = root.get("price");
		if (price == null || !price.isNumber()) {
			throw new RateProviderException("Gold API response for " + code + " has no numeric price");
		}
		BigDecimal rate = price.decimalValue();
		if (rate.signum() <= 0) {
			throw new RateProviderException("Gold API price for " + code + " is not positive");
		}
		if (rate.precision() - rate.scale() > MAX_INTEGER_DIGITS) {
			throw new RateProviderException("Gold API price for " + code + " is out of range");
		}
		if (rate.scale() > RATE_SCALE) {
			rate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
			if (rate.signum() <= 0) {
				throw new RateProviderException("Gold API price for " + code + " is not positive");
			}
		}
		return new ProviderQuote(rate, updatedAt(root, code), null);
	}

	private static Instant updatedAt(JsonNode root, String code) {
		JsonNode value = root.get("updatedAt");
		if (value == null || !value.isString()) {
			return null;
		}
		try {
			return Instant.parse(value.asString().trim());
		}
		catch (DateTimeParseException ex) {
			log.warn("Gold API updatedAt for {} is not an ISO-8601 instant; the fetch time will be used", code);
			return null;
		}
	}

	private JsonNode readTree(String body, String code) {
		if (body == null || body.isBlank()) {
			throw new RateProviderException("Gold API returned an empty response for " + code);
		}
		try {
			JsonNode root = jsonMapper.readTree(body);
			if (root == null || !root.isObject()) {
				throw new RateProviderException("Gold API returned an unexpected response for " + code);
			}
			return root;
		}
		catch (JacksonException ex) {
			throw new RateProviderException("Gold API returned malformed JSON for " + code);
		}
	}

	/**
	 * Maps an HTTP status to an exception. A 429 suspends the client until the {@code Retry-After} time when the
	 * header is a number of seconds, otherwise for the configured back-off.
	 */
	private RateProviderException failureFor(int status, HttpHeaders headers, String code) {
		if (status == 429) {
			Instant until = clock.instant().plus(retryAfter(headers));
			synchronized (this) {
				suspendedUntil = until;
			}
			log.warn("Gold API rate limit hit (HTTP 429); suspending requests until {}", until);
			return new ProviderRateLimitException("Gold API rate limit reached", until);
		}
		return new RateProviderException("Gold API error HTTP " + status + " for " + code);
	}

	private Duration retryAfter(HttpHeaders headers) {
		String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value != null) {
			try {
				long seconds = Long.parseLong(value.trim());
				if (seconds > 0) {
					return Duration.ofSeconds(seconds);
				}
			}
			catch (NumberFormatException ignored) {
				// An HTTP-date is not worth parsing; fall back to the configured back-off.
			}
		}
		return settings.getRateLimitBackoff();
	}

	/**
	 * Waits (on the injected clock) until the minimum spacing since the previous request has passed, after the
	 * suspension check. Synchronized so concurrent callers cannot exceed the limit.
	 */
	private synchronized void reserveRequest() {
		Instant now = clock.instant();
		if (now.isBefore(suspendedUntil)) {
			throw new ProviderRateLimitException("Gold API requests are suspended after a rate limit", suspendedUntil);
		}
		pruneOlderThanOneHour(now);
		if (settings.getRequestsPerHour() > 0 && recentRequests.size() >= settings.getRequestsPerHour()) {
			throw new ProviderRateLimitException("Gold API local hourly request limit reached",
					recentRequests.getFirst().plus(HOUR));
		}
		Instant start = now;
		if (settings.getRequestsPerMinute() > 0) {
			Duration spacing = Duration.ofMillis(60_000L / settings.getRequestsPerMinute());
			if (nextRequestAt.isAfter(now)) {
				sleeper.accept(Math.max(1, Duration.between(now, nextRequestAt).toMillis()));
				start = nextRequestAt;
			}
			nextRequestAt = start.plus(spacing);
		}
		recentRequests.addLast(start);
	}

	/**
	 * Requests this client would currently allow: the hourly budget minus the requests made in the last hour,
	 * or zero while suspended after a 429. Lets a caller fetch only what fits instead of provoking a refusal.
	 */
	public synchronized int remainingRequests() {
		Instant now = clock.instant();
		if (now.isBefore(suspendedUntil)) {
			return 0;
		}
		if (settings.getRequestsPerHour() <= 0) {
			return Integer.MAX_VALUE;
		}
		pruneOlderThanOneHour(now);
		return Math.max(0, settings.getRequestsPerHour() - recentRequests.size());
	}

	private void pruneOlderThanOneHour(Instant now) {
		Instant cutoff = now.minus(HOUR);
		while (!recentRequests.isEmpty() && !recentRequests.getFirst().isAfter(cutoff)) {
			recentRequests.removeFirst();
		}
	}

	private boolean hasApiKey() {
		return settings.getApiKey() != null && !settings.getApiKey().isBlank();
	}

	private static void sleepMillis(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RateProviderException("Interrupted while pacing Gold API requests", ex);
		}
	}

}
