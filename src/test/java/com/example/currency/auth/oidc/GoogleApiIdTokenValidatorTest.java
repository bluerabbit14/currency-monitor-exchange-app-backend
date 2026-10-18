package com.example.currency.auth.oidc;

import com.example.currency.auth.config.GoogleOidcProperties;
import com.example.currency.auth.exception.GoogleSignInUnavailableException;
import com.example.currency.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real signature, issuer, audience and expiry verification by Google's {@code GoogleIdTokenVerifier}. The only
 * stand-in is the network: a mock transport serves Google's certificates endpoint with a test certificate, and
 * the tokens are really RS256-signed with the matching (or a different) private key.
 */
class GoogleApiIdTokenValidatorTest {

	private static final String CLIENT_ID = "1234567890-abc.apps.googleusercontent.com";
	private static final GsonFactory JSON = GsonFactory.getDefaultInstance();

	private static PrivateKey signingKey;
	private static String certificatePem;

	@BeforeAll
	static void loadSigner() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = GoogleApiIdTokenValidatorTest.class.getResourceAsStream("/oidc/test-google-signer.p12")) {
			keyStore.load(in, "changeit".toCharArray());
		}
		signingKey = (PrivateKey) keyStore.getKey("google-test", "changeit".toCharArray());
		Certificate certificate = keyStore.getCertificate("google-test");
		certificatePem = "-----BEGIN CERTIFICATE-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(certificate.getEncoded())
				+ "\n-----END CERTIFICATE-----\n";
	}

	private GoogleApiIdTokenValidator validator() {
		return validatorFor(CLIENT_ID, certsTransport());
	}

	private static GoogleApiIdTokenValidator validatorFor(String clientId, HttpTransport transport) {
		GoogleOidcProperties properties = new GoogleOidcProperties();
		properties.setClientId(clientId);
		return new GoogleApiIdTokenValidator(properties, new GooglePublicKeysManager(transport, JSON));
	}

	private static HttpTransport certsTransport() {
		MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
				.setContentType("application/json")
				.addHeader("Cache-Control", "public, max-age=3600")
				.setContent("{\"test-kid\":" + quoted(certificatePem) + "}");
		return new MockHttpTransport.Builder().setLowLevelHttpResponse(response).build();
	}

	@Test
	void acceptsAValidlySignedTokenAndReturnsTheStableSubject() throws Exception {
		VerifiedGoogleIdentity identity = validator().verify(token(payload().build(), signingKey));

		assertEquals("google-sub-1", identity.subject());
		assertEquals("Person@Example.com", identity.email());
		assertTrue(identity.emailVerified());
		assertEquals("Pat Person", identity.name());
		assertEquals("https://lh3.googleusercontent.com/a/photo", identity.pictureUrl());
	}

	@Test
	void acceptsTheBareAccountsGoogleComIssuerToo() throws Exception {
		assertEquals("google-sub-1",
				validator().verify(token(payload().issuer("accounts.google.com").build(), signingKey)).subject());
	}

	@Test
	void rejectsATokenSignedByAnyoneElse() throws Exception {
		KeyPair attacker = KeyPairGenerator.getInstance("RSA").generateKeyPair();

		assertThrows(InvalidGoogleTokenException.class,
				() -> validator().verify(token(payload().build(), attacker.getPrivate())));
	}

	@Test
	void rejectsATokenWhosePayloadWasAlteredAfterSigning() throws Exception {
		String good = token(payload().build(), signingKey);
		String[] parts = good.split("\\.");
		String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				new String(Base64.getUrlDecoder().decode(parts[1])).replace("google-sub-1", "google-sub-2").getBytes());

		assertThrows(InvalidGoogleTokenException.class,
				() -> validator().verify(parts[0] + "." + forgedPayload + "." + parts[2]));
	}

	@Test
	void rejectsAnotherClientsToken() throws Exception {
		assertThrows(InvalidGoogleTokenException.class, () -> validator().verify(
				token(payload().audience("someone-elses-client.apps.googleusercontent.com").build(), signingKey)));
	}

	@Test
	void rejectsAnotherIssuer() throws Exception {
		assertThrows(InvalidGoogleTokenException.class, () -> validator().verify(
				token(payload().issuer("https://evil.example.com").build(), signingKey)));
	}

	@Test
	void rejectsAnExpiredToken() throws Exception {
		long hourAgo = Instant.now().minusSeconds(3600).getEpochSecond();

		assertThrows(InvalidGoogleTokenException.class, () -> validator().verify(
				token(payload().issuedAt(hourAgo - 3600).expiresAt(hourAgo).build(), signingKey)));
	}

	@Test
	void rejectsTokensMissingSubjectOrEmail() throws Exception {
		assertThrows(InvalidGoogleTokenException.class,
				() -> validator().verify(token(payload().subject(null).build(), signingKey)));
		assertThrows(InvalidGoogleTokenException.class,
				() -> validator().verify(token(payload().email(null).build(), signingKey)));
	}

	@Test
	void reportsAnUnverifiedEmailWithoutTrustingIt() throws Exception {
		VerifiedGoogleIdentity identity =
				validator().verify(token(payload().emailVerified(false).build(), signingKey));

		assertFalse(identity.emailVerified(), "the service refuses to use an unverified email");
	}

	@Test
	void rejectsGarbageBlankAndOversizedInput() {
		GoogleApiIdTokenValidator validator = validator();

		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify("not-a-jwt"));
		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify("a.b.c"));
		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify(""));
		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify("   "));
		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify(null));
		assertThrows(InvalidGoogleTokenException.class, () -> validator.verify("x".repeat(9000)));
	}

	@Test
	void anUnsignedAlgNoneTokenIsRejected() throws Exception {
		String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
		String body = Base64.getUrlEncoder().withoutPadding().encodeToString(
				JSON.toByteArray(payload().build()));

		assertThrows(InvalidGoogleTokenException.class, () -> validator().verify(header + "." + body + "."));
	}

	@Test
	void googlesKeysBeingUnreachableIsServiceUnavailableNotABadToken() throws Exception {
		HttpTransport broken = new MockHttpTransport() {
			@Override
			public com.google.api.client.http.LowLevelHttpRequest buildRequest(String method, String url)
					throws IOException {
				throw new IOException("network down");
			}
		};

		assertThrows(GoogleSignInUnavailableException.class, () ->
				validatorFor(CLIENT_ID, broken).verify(token(payload().build(), signingKey)));
	}

	@Test
	void isUnavailableUntilAClientIdIsConfigured() throws Exception {
		GoogleApiIdTokenValidator unconfigured = validatorFor("  ", certsTransport());

		assertThrows(GoogleSignInUnavailableException.class,
				() -> unconfigured.verify(token(payload().build(), signingKey)));
		assertNull(new GoogleOidcProperties().getClientId());
	}

	@org.springframework.context.annotation.Configuration
	@org.springframework.boot.context.properties.EnableConfigurationProperties(GoogleOidcProperties.class)
	static class WiringConfig {
	}

	@Test
	void springBuildsTheRealValidatorAndBindsTheClientIdProperty() {
		org.springframework.boot.test.context.runner.ApplicationContextRunner runner =
				new org.springframework.boot.test.context.runner.ApplicationContextRunner()
						.withUserConfiguration(WiringConfig.class, GoogleApiIdTokenValidator.class);

		// Not configured: the bean exists and answers "unavailable" instead of accepting anything.
		runner.run(context -> {
			org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
			assertThrows(GoogleSignInUnavailableException.class,
					() -> context.getBean(GoogleIdTokenValidator.class).verify("anything"));
		});
		// Configured through the property the application reads: tokens are now actually judged.
		runner.withPropertyValues("app.oidc.google.client-id=" + CLIENT_ID).run(context -> {
			org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
			assertEquals(CLIENT_ID, context.getBean(GoogleOidcProperties.class).getClientId());
			assertThrows(InvalidGoogleTokenException.class,
					() -> context.getBean(GoogleIdTokenValidator.class).verify("not-a-jwt"));
		});
	}


	// ----- helpers -----

	private static String token(GoogleIdToken.Payload payload, PrivateKey key) throws Exception {
		JsonWebSignature.Header header = new JsonWebSignature.Header().setAlgorithm("RS256").setType("JWT").setKeyId("test-kid");
		return JsonWebSignature.signUsingRsaSha256(key, JSON, header, payload);
	}

	private static PayloadBuilder payload() {
		return new PayloadBuilder();
	}

	private static String quoted(String value) {
		return "\"" + value.replace("\n", "\\n") + "\"";
	}

	private static final class PayloadBuilder {

		private String issuer = "https://accounts.google.com";
		private String audience = CLIENT_ID;
		private String subject = "google-sub-1";
		private String email = "Person@Example.com";
		private boolean emailVerified = true;
		private long issuedAt = Instant.now().getEpochSecond();
		private long expiresAt = Instant.now().plusSeconds(3600).getEpochSecond();

		PayloadBuilder issuer(String value) { issuer = value; return this; }
		PayloadBuilder audience(String value) { audience = value; return this; }
		PayloadBuilder subject(String value) { subject = value; return this; }
		PayloadBuilder email(String value) { email = value; return this; }
		PayloadBuilder emailVerified(boolean value) { emailVerified = value; return this; }
		PayloadBuilder issuedAt(long value) { issuedAt = value; return this; }
		PayloadBuilder expiresAt(long value) { expiresAt = value; return this; }

		GoogleIdToken.Payload build() {
			GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
			payload.setIssuer(issuer);
			payload.setAudience(audience);
			payload.setSubject(subject);
			payload.setEmail(email);
			payload.setEmailVerified(emailVerified);
			payload.setIssuedAtTimeSeconds(issuedAt);
			payload.setExpirationTimeSeconds(expiresAt);
			payload.set("name", "Pat Person");
			payload.set("picture", "https://lh3.googleusercontent.com/a/photo");
			return payload;
		}

	}

}
