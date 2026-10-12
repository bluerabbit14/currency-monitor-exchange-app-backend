package com.example.currency.user.dto;

import com.example.currency.user.validation.OptionalHttpUrl;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
		@Size(max = 100, message = "Display name must be at most 100 characters")
		String displayName,
		@Size(max = 2048, message = "Profile image URL must be at most 2048 characters")
		@OptionalHttpUrl
		String profileImageUrl
) {
}
