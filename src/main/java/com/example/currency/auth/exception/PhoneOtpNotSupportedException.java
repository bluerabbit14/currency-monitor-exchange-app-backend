package com.example.currency.auth.exception;

public class PhoneOtpNotSupportedException extends RuntimeException {

	public PhoneOtpNotSupportedException() {
		super("Phone OTP is not supported yet");
	}

}
