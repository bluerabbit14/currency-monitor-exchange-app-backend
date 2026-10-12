package com.example.currency.notification.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(UUID id) {
		super("Notification not found");
	}

}
