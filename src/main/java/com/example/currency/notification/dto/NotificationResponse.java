package com.example.currency.notification.dto;

import com.example.currency.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Inbox notification for the signed-in user")
public record NotificationResponse(
		@Schema(description = "Notification identifier") UUID id,
		@Schema(description = "Notification category", allowableValues = { "PRICE_ALERT" }) NotificationType type,
		@Schema(description = "Short title", example = "Price alert triggered") String title,
		@Schema(description = "User-facing message body") String message,
		@Schema(description = "Optional linked entity id, e.g. price alert", nullable = true) UUID referenceId,
		@Schema(description = "Whether the user has read this notification") boolean isRead,
		@Schema(description = "When the notification was created") Instant createdAt,
		@Schema(description = "When the notification was first marked read", nullable = true) Instant readAt
) {
}
