package com.example.currency.auth.exception;

/** The account exists but may not sign in (suspended or deleted). */
public class AccountNotActiveException extends RuntimeException {

	public static final String CODE = "ACCOUNT_NOT_ACTIVE";

	public AccountNotActiveException() {
		super("This account cannot sign in");
	}

}
