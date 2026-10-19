package com.example.currency.feedback.dto;

import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A stored piece of feedback")
public record FeedbackResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "5", nullable = true) Integer rating,
		@Schema(example = "SUGGESTION") FeedbackCategory category,
		@Schema(example = "Please add dark mode", nullable = true) String message,
		@Schema(example = "1.4.2", nullable = true) String appVersion,
		@Schema(example = "ANDROID") FeedbackPlatform platform,
		@Schema(example = "NEW") FeedbackStatus status,
		@Schema(example = "2026-10-19T09:31:16Z") Instant createdAt,
		@Schema(example = "2026-10-19T09:31:16Z") Instant updatedAt
) {
}
