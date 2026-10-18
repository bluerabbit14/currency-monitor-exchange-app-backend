package com.example.currency.auth.exception;

/** Google did not verify the email address, so it cannot be used to create or link an account. */
public class GoogleEmailNotVerifiedException extends RuntimeException {

	public static final String CODE = "GOOGLE_EMAIL_NOT_VERIFIED";

	public GoogleEmailNotVerifiedException() {
		super("The Google account email is not verified");
	}

}
