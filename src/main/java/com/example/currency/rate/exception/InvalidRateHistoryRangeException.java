package com.example.currency.rate.exception;

public class InvalidRateHistoryRangeException extends RuntimeException {

	public static final String CODE = "INVALID_RATE_HISTORY_RANGE";

	public InvalidRateHistoryRangeException(String message) {
		super(message);
	}

}
