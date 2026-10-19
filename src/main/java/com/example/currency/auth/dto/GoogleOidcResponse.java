package com.example.currency.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Result of a Google sign-in step. {@code SIGNED_IN} carries Yakku tokens. {@code LINK_VERIFICATION_REQUIRED}
 * means the verified Google email already belongs to a Yakku account: an OTP was emailed to that address and
 * nothing is linked until {@code POST /api/v1/auth/oidc/google/link/verify} succeeds with {@code linkToken} + OTP.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleOidcResponse(
		Status status,
		String accessToken,
		String refreshToken,
		String tokenType,
		Long expiresInSeconds,
		Boolean newUser,
		UUID baseCurrencyId,
		String linkToken,
		String maskedEmail,
		Long linkExpiresInSeconds
) {

	public enum Status {
		SIGNED_IN,
		LINK_VERIFICATION_REQUIRED
	}

	public static GoogleOidcResponse signedIn(AuthTokenResponse tokens, boolean newUser) {
		return new GoogleOidcResponse(Status.SIGNED_IN, tokens.accessToken(), tokens.refreshToken(),
				tokens.tokenType(), tokens.expiresInSeconds(), newUser,
				tokens.baseCurrencyId(), null, null, null);
	}

	public static GoogleOidcResponse linkRequired(String linkToken, String maskedEmail, long expiresInSeconds) {
		return new GoogleOidcResponse(Status.LINK_VERIFICATION_REQUIRED, null, null, null, null, null, null,
				linkToken, maskedEmail, expiresInSeconds);
	}

}
