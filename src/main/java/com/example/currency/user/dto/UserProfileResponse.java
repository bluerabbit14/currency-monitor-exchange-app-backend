package com.example.currency.user.dto;

import com.example.currency.asset.dto.AssetResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String phoneNumber,
		String displayName,
		String profileImageUrl,
		@Schema(description = "The plan the user is currently on; the default plan when there is no active subscription")
		UserPlanResponse currentPlan,
		@Schema(description = "The currency the user compares prices against; null until they pick one")
		AssetResponse baseCurrency,
		@Schema(description = "Whether push notifications are allowed (the user's push preference; true until they turn it off)")
		boolean notificationsEnabled
) {
}
