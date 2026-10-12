package com.example.currency.device.dto;

import com.example.currency.user.enums.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Register or refresh an Android device FCM token for the signed-in user")
public record RegisterDeviceRequest(
		@Schema(
				description = "FCM registration token supplied by the Android app. Never returned in responses.",
				example = "example-fcm-registration-token",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "FCM token is required")
		@Size(max = 4096, message = "FCM token must be at most 4096 characters")
		String fcmToken,

		@Schema(
				description = "Device platform. Only ANDROID is supported.",
				example = "ANDROID",
				allowableValues = {"ANDROID"},
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Platform is required")
		DevicePlatform platform,

		@Schema(description = "Optional display name for the device", example = "My Android Phone")
		@Size(max = 120, message = "Device name must be at most 120 characters")
		String deviceName
) {
}
