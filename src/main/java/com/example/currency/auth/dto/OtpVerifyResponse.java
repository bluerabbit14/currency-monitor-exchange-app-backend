package com.example.currency.auth.dto;

import java.util.UUID;

/**
 * {@code newUser} is true when this verification created the account. {@code baseCurrencyId} is null until the
 * user has chosen a base currency; the app then asks for one before opening.
 */
public record OtpVerifyResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds,
		boolean newUser,
		UUID baseCurrencyId
) {

	public static OtpVerifyResponse of(AuthTokenResponse tokens, boolean newUser) {
		return new OtpVerifyResponse(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.tokenType(),
				tokens.expiresInSeconds(),
				newUser,
				tokens.baseCurrencyId());
	}

}
