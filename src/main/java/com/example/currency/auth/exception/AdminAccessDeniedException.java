package com.example.currency.auth.exception;

public class AdminAccessDeniedException extends RuntimeException {

	public AdminAccessDeniedException() {
		super("Admin access required");
	}

}
