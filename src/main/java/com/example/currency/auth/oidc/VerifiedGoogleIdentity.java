package com.example.currency.auth.oidc;

/**
 * Claims taken from a Google ID token whose signature, issuer, audience and expiry have been verified.
 * {@code subject} is Google's stable account id; {@code email} is only a contact address, never an identity.
 */
public record VerifiedGoogleIdentity(
		String subject,
		String email,
		boolean emailVerified,
		String name,
		String pictureUrl
) {
}
