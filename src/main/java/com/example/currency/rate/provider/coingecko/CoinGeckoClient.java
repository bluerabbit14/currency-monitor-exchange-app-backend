package com.example.currency.rate.provider.coingecko;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * CoinGecko {@code GET /simple/price?ids=a,b&vs_currencies=x,y&include_last_updated_at=true}: one request
 * returns every requested coin and currency, and costs one call of the monthly quota.
 * <p>
 * Results are keyed {@code "<coin id>:<vs currency>"}, the same format as {@code asset_pairs.provider_pair_code}.
 * Only valid prices are returned; a missing coin, a missing currency or an unusable price is left out (and
 * logged) so the caller never stores it. {@link ProviderQuote#observedAt()} comes from the coin's
 * {@code last_updated_at}, and is null when the response has none. There is no observation id.
 * <p>
 * The client owns quota safety: a local monthly call guard and a suspension after HTTP 429. It never retries.
 * The API key travels in a header, never in the URL, and is never logged.
 */
@Component
public class CoinGeckoClient {

	private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);

	/** Highest integer-digit count the NUMERIC(28, 12) columns can hold. */
	private static final int MAX_INTEGER_DIGITS = 16;
	private static final int RATE_SCALE = 12;
	private static final String LAST_UPDATED_AT = "last_updated_at";

	private final RestClient restClient;
	private final RateProperties.CoinGecko settings;
	private final Clock clock;
	private final JsonMapper jsonMapper = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.build();

	private YearMonth budgetMonth;
	private int budgetUsed;
	private Instant suspendedUntil = Instant.MIN;

	public CoinGeckoClient(
			@Qualifier("coinGeckoRestClient") RestClient restClient,
			RateProperties properties,
			Clock clock) {
		this.restClient = restClient;
		this.settings = properties.getCoingecko();
		this.clock = clock;
	}

	public boolean isConfigured() {
		return settings.getApiKey() != null && !settings.getApiKey().isBlank();
	}

	/**
	 * @param coinIds      CoinGecko coin ids, for example {@code bitcoin}
	 * @param vsCurrencies quote currency codes, for example {@code usd}
	 * @return valid quotes keyed {@code "<coin id>:<vs currency>"}, lower case; possibly fewer than requested
	 * @throws ProviderRateLimitException on HTTP 429, or when the local monthly guard or a suspension holds the call back
	 * @throws RateProviderException for any other failure or an unusable response body
	 */
	public Map<String, ProviderQuote> fetchPrices(Collection<String> coinIds, Collection<String> vsCurrencies) {
		if (!isConfigured()) {
			throw new RateProviderException("CoinGecko API key is not configured");
		}
		TreeSet<String> ids = normalise(coinIds);
		TreeSet<String> currencies = normalise(vsCurrencies);
		if (ids.isEmpty() || currencies.isEmpty()) {
			throw new RateProviderException("CoinGecko request needs at least one coin id and one currency");
		}

		reserveCall();

		String idList = String.join(",", ids);
		String currencyList = String.join(",", currencies);
		String body;
		try {
			body = restClient.get()
					.uri(uri -> uri.path("/simple/price")
							.queryParam("ids", "{ids}")
							.queryParam("vs_currencies", "{vs}")
							.queryParam("include_last_updated_at", "true")
							.build(idList, currencyList))
					.header(settings.getApiKeyHeader(), settings.getApiKey().trim())
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException ex) {
			throw failureFor(ex.getStatusCode().value(), ex.getResponseHeaders());
		}
		catch (RestClientException ex) {
			// Timeouts and connection failures. The message is omitted: it can echo the request URL.
			throw new RateProviderException("CoinGecko request failed: " + ex.getClass().getSimpleName(), ex);
		}
		return parse(body, ids, currencies);
	}

	private Map<String, ProviderQuote> parse(String body, TreeSet<String> ids, TreeSet<String> currencies) {
		JsonNode root = readTree(body);
		Map<String, ProviderQuote> quotes = new LinkedHashMap<>();
		int missingCoins = 0;
		int unusable = 0;

		for (String id : ids) {
			JsonNode coin = root.get(id);
			if (coin == null || !coin.isObject()) {
				missingCoins++;
				log.warn("CoinGecko response has no data for coin '{}'", id);
				continue;
			}
			Instant observedAt = lastUpdatedAt(coin);
			for (String currency : currencies) {
				JsonNode price = coin.get(currency);
				if (price == null || price.isNull()) {
					// Not every coin/currency combination in the cross product is a configured pair.
					log.debug("CoinGecko response has no {} price for '{}'", currency, id);
					continue;
				}
				BigDecimal rate = validRate(price);
				if (rate == null) {
					unusable++;
					log.warn("CoinGecko returned an unusable {} price for '{}'", currency, id);
					continue;
				}
				quotes.put(id + ":" + currency, new ProviderQuote(rate, observedAt, null));
			}
		}
		log.debug("CoinGecko response: {} price(s), {} coin(s) missing, {} unusable",
				quotes.size(), missingCoins, unusable);
		return quotes;
	}

	private JsonNode readTree(String body) {
		if (body == null || body.isBlank()) {
			throw new RateProviderException("CoinGecko returned an empty response");
		}
		try {
			JsonNode root = jsonMapper.readTree(body);
			if (root == null || !root.isObject()) {
				throw new RateProviderException("CoinGecko returned an unexpected response");
			}
			return root;
		}
		catch (JacksonException ex) {
			throw new RateProviderException("CoinGecko returned malformed JSON");
		}
	}

	/**
	 * Only JSON numbers are accepted; a string such as "123" is treated as unusable rather than guessed at.
	 */
	private static BigDecimal validRate(JsonNode price) {
		if (!price.isNumber()) {
			return null;
		}
		BigDecimal rate = price.decimalValue();
		if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.precision() - rate.scale() > MAX_INTEGER_DIGITS) {
			return null;
		}
		if (rate.scale() > RATE_SCALE) {
			rate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
			if (rate.compareTo(BigDecimal.ZERO) <= 0) {
				return null;
			}
		}
		return rate;
	}

	/**
	 * Epoch seconds from {@code last_updated_at}; null when absent or not a positive whole number.
	 */
	private static Instant lastUpdatedAt(JsonNode coin) {
		JsonNode value = coin.get(LAST_UPDATED_AT);
		if (value == null || !value.isIntegralNumber() || value.asLong() <= 0) {
			return null;
		}
		return Instant.ofEpochSecond(value.asLong());
	}

	/**
	 * Maps an HTTP status to an exception. A 429 suspends the client until the {@code Retry-After} time when
	 * the header is a number of seconds, otherwise for the configured back-off.
	 */
	private RateProviderException failureFor(int status, HttpHeaders headers) {
		if (status == 429) {
			Instant until = clock.instant().plus(retryAfter(headers));
			synchronized (this) {
				suspendedUntil = until;
			}
			log.warn("CoinGecko rate limit hit (HTTP 429); suspending requests until {}", until);
			return new ProviderRateLimitException("CoinGecko rate limit reached", until);
		}
		return new RateProviderException("CoinGecko error HTTP " + status);
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
	 * Counts one call for the coming request, after the suspension and local-budget checks.
	 */
	private synchronized void reserveCall() {
		Instant now = clock.instant();
		if (now.isBefore(suspendedUntil)) {
			throw new ProviderRateLimitException("CoinGecko requests are suspended after a rate limit", suspendedUntil);
		}
		YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
		if (!month.equals(budgetMonth)) {
			budgetMonth = month;
			budgetUsed = 0;
		}
		if (budgetUsed >= settings.getMonthlyRequestLimit()) {
			throw new ProviderRateLimitException("CoinGecko local monthly request limit reached",
					month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
		}
		budgetUsed++;
	}

	private static TreeSet<String> normalise(Collection<String> values) {
		TreeSet<String> result = new TreeSet<>();
		if (values != null) {
			for (String value : values) {
				if (value != null && !value.isBlank()) {
					result.add(value.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return result;
	}

}
