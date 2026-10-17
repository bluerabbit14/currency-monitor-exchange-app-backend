package com.example.currency.rate.provider;

public class RateProviderException extends RuntimeException {

	public RateProviderException(String message) {
		super(message);
	}

	public RateProviderException(String message, Throwable cause) {
		super(message, cause);
	}

}
