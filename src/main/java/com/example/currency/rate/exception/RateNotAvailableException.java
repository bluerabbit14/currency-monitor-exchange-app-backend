package com.example.currency.rate.exception;

public class RateNotAvailableException extends RuntimeException {

	public static final String CODE = "RATE_NOT_AVAILABLE";

	public RateNotAvailableException(String base, String quote) {
		super("Rate not available for pair: " + base + "/" + quote);
	}

}
