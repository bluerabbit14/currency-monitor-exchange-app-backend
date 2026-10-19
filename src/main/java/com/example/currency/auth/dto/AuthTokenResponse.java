package com.example.currency.auth.dto;

import java.util.UUID;

public record AuthTokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds,
		UUID baseCurrencyId
) {
}
