package com.example.currency.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Partial update of notification preferences; omitted fields remain unchanged")
public record UpdateNotificationPreferenceRequest(
		@Schema(description = "Enable or disable push notifications", example = "true")
		Boolean pushEnabled,
		@Schema(description = "Enable or disable in-app notifications", example = "true")
		Boolean inAppEnabled,
		@Schema(description = "Enable or disable email notifications", example = "false")
		Boolean emailEnabled
) {
}
