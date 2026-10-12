package com.example.currency.notification.exception;

public class EmptyNotificationPreferenceUpdateException extends RuntimeException {

	public EmptyNotificationPreferenceUpdateException() {
		super("At least one preference field must be provided");
	}

}
