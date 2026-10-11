package com.example.currency.auth.exception;

public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException() {
		super("Unable to send OTP");
	}

}
