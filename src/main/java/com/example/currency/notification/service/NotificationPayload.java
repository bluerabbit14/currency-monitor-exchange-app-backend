package com.example.currency.notification.service;

import com.example.currency.notification.enums.NotificationType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FCM {@code data} payload the app uses to open the right screen when a push is tapped.
 * Carries only the event type and resource ids, never private content. Keys must match
 * {@code NotificationDestination.fromData} in the Flutter app.
 */
public final class NotificationPayload {

	public static final String TYPE = "type";
	public static final String ALERT_ID = "alertId";
	public static final String NOTIFICATION_ID = "notificationId";

	private NotificationPayload() {
	}

	public static Map<String, String> of(NotificationType type, UUID alertId, UUID notificationId) {
		Map<String, String> data = new HashMap<>();
		data.put(TYPE, type.name());
		if (alertId != null) {
			data.put(ALERT_ID, alertId.toString());
		}
		if (notificationId != null) {
			data.put(NOTIFICATION_ID, notificationId.toString());
		}
		return data;
	}

}
