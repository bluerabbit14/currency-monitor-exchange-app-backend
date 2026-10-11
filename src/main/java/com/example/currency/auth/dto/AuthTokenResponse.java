package com.example.currency.auth.dto;

public record AuthTokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds
) {
}
