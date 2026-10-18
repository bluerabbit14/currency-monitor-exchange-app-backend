package com.example.currency.auth.exception;

/** The Google identity and the Yakku account cannot be linked without merging or replacing another link. */
public class GoogleAccountConflictException extends RuntimeException {

	public static final String CODE = "GOOGLE_ACCOUNT_CONFLICT";

	public GoogleAccountConflictException(String message) {
		super(message);
	}

}
