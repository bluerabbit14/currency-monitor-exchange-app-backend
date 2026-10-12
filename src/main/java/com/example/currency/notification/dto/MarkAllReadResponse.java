package com.example.currency.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of marking all unread notifications as read")
public record MarkAllReadResponse(
		@Schema(description = "Number of notifications newly marked as read", example = "3") int updatedCount
) {
}
