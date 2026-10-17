package com.example.currency.user.enums;

public enum UserStatus {
	ACTIVE,
	SUSPENDED,
	/** Deletion requested; the account is restored if the user signs in before the grace period ends. */
	PENDING_DELETION,
	/** Personal data erased; only an anonymized row remains when payment records must be kept. */
	DELETED
}
