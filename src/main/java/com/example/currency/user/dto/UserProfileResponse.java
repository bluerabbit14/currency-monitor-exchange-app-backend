package com.example.currency.user.dto;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.plan.dto.PlanResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String phoneNumber,
		String displayName,
		String profileImageUrl,
		Instant createdAt,
		Instant updatedAt,
		@Schema(description = "The plan the user is currently on; the default plan when there is no active subscription")
		PlanResponse currentPlan,
		@Schema(description = "The currency the user compares prices against; null until they pick one")
		AssetResponse baseCurrency
) {
}
