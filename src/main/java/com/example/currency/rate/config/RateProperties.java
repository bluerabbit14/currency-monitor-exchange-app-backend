package com.example.currency.rate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rates")
public class RateProperties {

	/**
	 * Maximum age of {@code observedAt} before a cached rate is considered stale.
	 * Review this threshold after a rate provider is selected for production.
	 */
	private Duration staleAfter = Duration.ofMinutes(15);

	public Duration getStaleAfter() {
		return staleAfter;
	}

	public void setStaleAfter(Duration staleAfter) {
		this.staleAfter = staleAfter;
	}

}
