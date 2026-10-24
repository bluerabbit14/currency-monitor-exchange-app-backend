package com.example.currency.notification.service;

import com.example.currency.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationPayloadTest {

	@Test
	void includesTypeAndIds() {
		UUID alertId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();

		Map<String, String> data = NotificationPayload.of(NotificationType.PRICE_ALERT, alertId, notificationId);

		assertEquals(Map.of(
				"type", "PRICE_ALERT",
				"alertId", alertId.toString(),
				"notificationId", notificationId.toString()), data);
	}

	@Test
	void omitsMissingIds() {
		Map<String, String> data = NotificationPayload.of(NotificationType.PRICE_ALERT, null, null);

		assertEquals(Map.of("type", "PRICE_ALERT"), data);
	}

}
