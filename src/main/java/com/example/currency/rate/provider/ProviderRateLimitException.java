package com.example.currency.rate.provider;

import java.time.Instant;

/**
 * The provider refused the call because of quota, or the client held the call back to protect quota.
 * Collection stops requesting from this provider for the rest of the run.
 */
public class ProviderRateLimitException extends RateProviderException {

	private final Instant retryAfter;

	public ProviderRateLimitException(String message, Instant retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	/**
	 * Earliest time the client will call the provider again.
	 */
	public Instant getRetryAfter() {
		return retryAfter;
	}

}
