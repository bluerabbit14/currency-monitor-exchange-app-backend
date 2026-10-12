package com.example.currency.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Notification channel preferences for the signed-in user")
public record NotificationPreferenceResponse(
		@Schema(description = "Whether push notifications are enabled", example = "true")
		boolean pushEnabled,
		@Schema(description = "Whether in-app notifications are enabled", example = "true")
		boolean inAppEnabled,
		@Schema(description = "Whether email notifications are enabled", example = "false")
		boolean emailEnabled,
		@Schema(description = "When the preferences record was created", example = "2026-10-12T04:00:00Z")
		Instant createdAt,
		@Schema(description = "When the preferences record was last updated", example = "2026-10-12T04:00:00Z")
		Instant updatedAt
) {
}
