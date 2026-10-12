package com.example.currency.rate.exception;

public class UnsupportedPairException extends RuntimeException {

	public static final String CODE = "UNSUPPORTED_PAIR";

	public UnsupportedPairException(String base, String quote) {
		super("Unsupported pair: " + base + "/" + quote);
	}

}
