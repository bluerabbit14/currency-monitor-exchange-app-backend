package com.example.currency.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String phoneNumber,
		String displayName,
		String profileImageUrl,
		Instant createdAt,
		Instant updatedAt
) {
}
