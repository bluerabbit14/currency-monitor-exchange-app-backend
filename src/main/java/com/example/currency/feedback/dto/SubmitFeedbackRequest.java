package com.example.currency.feedback.dto;

import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "A star rating, a written message, or both. At least one is required.")
public record SubmitFeedbackRequest(
		@Schema(description = "Star rating, 1 to 5", example = "5", nullable = true)
		@Min(value = 1, message = "rating must be between 1 and 5")
		@Max(value = 5, message = "rating must be between 1 and 5")
		Integer rating,
		@Schema(description = "Defaults to GENERAL", example = "SUGGESTION",
				allowableValues = {"BUG", "SUGGESTION", "GENERAL"})
		FeedbackCategory category,
		@Schema(description = "What the user wants to say", example = "Please add dark mode", nullable = true)
		@Size(max = 2000, message = "message must be at most 2000 characters")
		String message,
		@Schema(description = "App version the feedback comes from", example = "1.4.2", nullable = true)
		@Size(max = 30, message = "appVersion must be at most 30 characters")
		String appVersion,
		@Schema(description = "Defaults to ANDROID", example = "IOS", allowableValues = {"ANDROID", "IOS", "WEB"})
		FeedbackPlatform platform
) {

	@JsonIgnore
	@AssertTrue(message = "Provide a rating or a message")
	public boolean isContentPresent() {
		return rating != null || (message != null && !message.isBlank());
	}

}
