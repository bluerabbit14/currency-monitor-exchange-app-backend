package com.example.currency.feedback.dto;

import com.example.currency.feedback.enums.FeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Move a piece of feedback to a new resolution status")
public record UpdateFeedbackStatusRequest(
		@Schema(example = "RESOLVED", allowableValues = {"NEW", "REVIEWED", "RESOLVED"},
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "status is required")
		FeedbackStatus status
) {
}
