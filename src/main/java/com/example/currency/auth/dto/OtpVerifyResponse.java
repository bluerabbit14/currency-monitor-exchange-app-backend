package com.example.currency.auth.dto;

/** {@code newUser} is true when this verification created the account. */
public record OtpVerifyResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds,
		boolean newUser
) {

	public static OtpVerifyResponse of(AuthTokenResponse tokens, boolean newUser) {
		return new OtpVerifyResponse(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.tokenType(),
				tokens.expiresInSeconds(),
				newUser);
	}

}
