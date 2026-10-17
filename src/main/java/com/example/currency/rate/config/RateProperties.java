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
		 * Delay between the end of one run and the start of the next. The default of six minutes keeps three
		 * Twelve Data pairs near 720 credits a day, inside the free plan's 800.
		 */
		private Duration interval = Duration.ofMinutes(6);

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

}
