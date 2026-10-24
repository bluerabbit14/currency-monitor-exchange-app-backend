package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
		@NotBlank(message = "Refresh token is required")
		String refreshToken,

		// This device's FCM token; when sent, only this device's registration is
		// deactivated along with the session.
		@Size(max = 4096, message = "FCM token must be at most 4096 characters")
		String fcmToken
) {
}
