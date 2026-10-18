package com.example.currency.auth.oidc;

import com.example.currency.auth.config.GoogleOidcProperties;
import com.example.currency.auth.exception.GoogleSignInUnavailableException;
import com.example.currency.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Verifies Google ID tokens with Google's own {@link GoogleIdTokenVerifier}: RS256 signature against Google's
 * published keys, issuer ({@code accounts.google.com}), audience (the configured Web client ID) and expiry.
 * On top of that, a token without a subject or an email is rejected. Nothing the client decoded is trusted.
 */
@Component
public class GoogleApiIdTokenValidator implements GoogleIdTokenValidator {

	private static final Logger log = LoggerFactory.getLogger(GoogleApiIdTokenValidator.class);

	/** Real ID tokens are about 1 KB; refuse anything absurd before parsing it. */
	private static final int MAX_TOKEN_LENGTH = 8192;
	private static final int MAX_SUBJECT_LENGTH = 255;

	private final GoogleOidcProperties properties;
	private final GoogleIdTokenVerifier verifier;

	@Autowired
	public GoogleApiIdTokenValidator(GoogleOidcProperties properties) {
		this(properties, null);
	}

	/** For tests: supply a key source instead of fetching Google's keys. */
	GoogleApiIdTokenValidator(GoogleOidcProperties properties, GooglePublicKeysManager keysManager) {
		this.properties = properties;
		if (!properties.isConfigured()) {
			this.verifier = null;
			return;
		}
		GoogleIdTokenVerifier.Builder builder = keysManager != null
				? new GoogleIdTokenVerifier.Builder(keysManager)
				: new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance());
		this.verifier = builder.setAudience(List.of(properties.getClientId().trim())).build();
	}

	@Override
	public VerifiedGoogleIdentity verify(String idToken) {
		if (verifier == null) {
			throw new GoogleSignInUnavailableException("Google sign-in is not configured");
		}
		if (idToken == null || idToken.isBlank() || idToken.length() > MAX_TOKEN_LENGTH) {
			throw new InvalidGoogleTokenException();
		}
		GoogleIdToken token;
		try {
			token = GoogleIdToken.parse(verifier.getJsonFactory(), idToken.trim());
		}
		catch (IOException | IllegalArgumentException ex) {
			throw new InvalidGoogleTokenException();
		}
		boolean valid;
		try {
			valid = verifier.verify(token);
		}
		catch (GeneralSecurityException | IOException ex) {
			// The token parsed, so this is Google's key set being unreachable or unreadable: not the caller's fault.
			log.warn("Google signing keys could not be loaded: {}", ex.getClass().getSimpleName());
			throw new GoogleSignInUnavailableException("Google sign-in is temporarily unavailable");
		}
		if (!valid) {
			throw new InvalidGoogleTokenException();
		}
		GoogleIdToken.Payload payload = token.getPayload();
		String subject = payload.getSubject();
		String email = payload.getEmail();
		if (subject == null || subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH
				|| email == null || email.isBlank()) {
			throw new InvalidGoogleTokenException();
		}
		return new VerifiedGoogleIdentity(
				subject,
				email,
				Boolean.TRUE.equals(payload.getEmailVerified()),
				stringClaim(payload, "name"),
				stringClaim(payload, "picture"));
	}

	private static String stringClaim(GoogleIdToken.Payload payload, String name) {
		Object value = payload.get(name);
		return value instanceof String text && !text.isBlank() ? text.trim() : null;
	}

}
