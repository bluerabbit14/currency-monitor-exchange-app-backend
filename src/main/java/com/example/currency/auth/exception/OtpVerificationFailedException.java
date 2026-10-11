package com.example.currency.auth.exception;

public class OtpVerificationFailedException extends RuntimeException {

	public OtpVerificationFailedException() {
		super("Invalid or expired OTP.");
	}

}
