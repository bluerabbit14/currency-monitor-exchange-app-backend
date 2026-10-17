package com.example.currency.rate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rates")
public class RateProperties {

	/**
	 * Maximum age of {@code observedAt} before a cached rate is considered stale.
	 * Keep the collection interval comfortably below this value.
	 */
	private Duration staleAfter = Duration.ofMinutes(15);

	private final Collection collection = new Collection();

	private final TwelveData twelvedata = new TwelveData();

	private final CoinGecko coingecko = new CoinGecko();

	public CoinGecko getCoingecko() {
		return coingecko;
	}

	public Duration getStaleAfter() {
		return staleAfter;
	}

	public void setStaleAfter(Duration staleAfter) {
		this.staleAfter = staleAfter;
	}

	public Collection getCollection() {
		return collection;
	}

	public TwelveData getTwelvedata() {
		return twelvedata;
	}

	public static class Collection {

		/**
		 * Master switch for scheduled collection. Off by default so nothing calls a provider unless enabled.
		 */
		private boolean enabled = false;

		/**
		 * Delay between the end of one run and the start of the next. The default of twelve minutes keeps six
		 * Twelve Data pairs near 720 credits a day, inside the free plan's 800.
		 */
		private Duration interval = Duration.ofMinutes(12);

		private Duration initialDelay = Duration.ofSeconds(30);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getInterval() {
			return interval;
		}

		public void setInterval(Duration interval) {
			this.interval = interval;
		}

		public Duration getInitialDelay() {
			return initialDelay;
		}

		public void setInitialDelay(Duration initialDelay) {
			this.initialDelay = initialDelay;
		}

	}

	public static class TwelveData {

		/**
		 * Read from the environment (TWELVEDATA_API_KEY). Never logged.
		 */
		private String apiKey;

		private String baseUrl = "https://api.twelvedata.com";

		private Duration connectTimeout = Duration.ofSeconds(3);

		private Duration readTimeout = Duration.ofSeconds(10);

		/**
		 * Paces requests below the plan's per-minute credit limit (free plan: 8). One request costs one credit.
		 */
		private int requestsPerMinute = 6;

		/**
		 * Local, in-memory estimate of credits to spend per UTC day (free plan: 800). It resets on restart and
		 * is not shared between instances, so it is a safety guard, not an exact remaining balance.
		 */
		private int dailyRequestLimit = 750;

		/**
		 * How long to stop calling the provider after a per-minute rate-limit response.
		 */
		private Duration rateLimitBackoff = Duration.ofMinutes(2);

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		public int getRequestsPerMinute() {
			return requestsPerMinute;
		}

		public void setRequestsPerMinute(int requestsPerMinute) {
			this.requestsPerMinute = requestsPerMinute;
		}

		public int getDailyRequestLimit() {
			return dailyRequestLimit;
		}

		public void setDailyRequestLimit(int dailyRequestLimit) {
			this.dailyRequestLimit = dailyRequestLimit;
		}

		public Duration getRateLimitBackoff() {
			return rateLimitBackoff;
		}

		public void setRateLimitBackoff(Duration rateLimitBackoff) {
			this.rateLimitBackoff = rateLimitBackoff;
		}

	}

	public static class CoinGecko {

		/**
		 * Master switch for the CoinGecko scheduler. Off by default; it also needs an API key to register.
		 */
		private boolean enabled = false;

		/**
		 * Delay between the end of one run and the start of the next. One batched request per run, so five
		 * minutes is about 8,640 requests in 30 days (Demo plan: 10,000 a month).
		 */
		private Duration interval = Duration.ofMinutes(5);

		private Duration initialDelay = Duration.ofSeconds(45);

		/**
		 * Read from the environment (COINGECKO_API_KEY). Never logged.
		 */
		private String apiKey;

		/**
		 * Demo keys use {@code x-cg-demo-api-key}; Pro keys use {@code x-cg-pro-api-key} with the Pro base URL.
		 */
		private String apiKeyHeader = "x-cg-demo-api-key";

		private String baseUrl = "https://api.coingecko.com/api/v3";

		private Duration connectTimeout = Duration.ofSeconds(3);

		private Duration readTimeout = Duration.ofSeconds(10);

		/**
		 * Local, in-memory estimate of requests to spend per UTC month. It resets on restart and is not shared
		 * between instances or other users of the same key, so keep it below the plan quota with headroom.
		 */
		private int monthlyRequestLimit = 9000;

		/**
		 * How long to stop calling the provider after a 429 that carries no usable Retry-After header.
		 */
		private Duration rateLimitBackoff = Duration.ofMinutes(15);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getInterval() {
			return interval;
		}

		public void setInterval(Duration interval) {
			this.interval = interval;
		}

		public Duration getInitialDelay() {
			return initialDelay;
		}

		public void setInitialDelay(Duration initialDelay) {
			this.initialDelay = initialDelay;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getApiKeyHeader() {
			return apiKeyHeader;
		}

		public void setApiKeyHeader(String apiKeyHeader) {
			this.apiKeyHeader = apiKeyHeader;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		public int getMonthlyRequestLimit() {
			return monthlyRequestLimit;
		}

		public void setMonthlyRequestLimit(int monthlyRequestLimit) {
			this.monthlyRequestLimit = monthlyRequestLimit;
		}

		public Duration getRateLimitBackoff() {
			return rateLimitBackoff;
		}

		public void setRateLimitBackoff(Duration rateLimitBackoff) {
			this.rateLimitBackoff = rateLimitBackoff;
		}

	}

}
