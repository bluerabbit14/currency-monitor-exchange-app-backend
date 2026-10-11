package com.example.currency.auth.service;

import com.example.currency.auth.config.JwtProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.entity.RefreshSession;
import com.example.currency.auth.exception.InvalidRefreshTokenException;
import com.example.currency.auth.repository.RefreshSessionRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TokenService {

	private static final String TOKEN_TYPE = "Bearer";
	private static final String HMAC_SHA256 = "HmacSHA256";

	private final RefreshSessionRepository refreshSessionRepository;
	private final JwtProperties jwtProperties;
	private final SecureRandom secureRandom = new SecureRandom();
	private final byte[] jwtSecret;

	public TokenService(RefreshSessionRepository refreshSessionRepository, JwtProperties jwtProperties) {
		this.refreshSessionRepository = refreshSessionRepository;
		this.jwtProperties = jwtProperties;
		String secret = jwtProperties.getSecret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("JWT secret is not configured");
		}
		this.jwtSecret = secret.getBytes(StandardCharsets.UTF_8);
	}

	@Transactional
	public AuthTokenResponse issue(User user) {
		String refreshToken = newRefreshToken();
		RefreshSession session = new RefreshSession();
		session.setUser(user);
		session.setTokenHash(sha256(refreshToken));
		session.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTtlSeconds()));
		refreshSessionRepository.save(session);
		return new AuthTokenResponse(
				accessToken(user.getId()),
				refreshToken,
				TOKEN_TYPE,
				jwtProperties.getAccessTtlSeconds());
	}

	@Transactional
	public AuthTokenResponse refresh(String refreshToken) {
		RefreshSession session = findActive(refreshToken);
		session.setRevokedAt(Instant.now());
		return issue(session.getUser());
	}

	@Transactional
	public void logout(String refreshToken) {
		findActive(refreshToken).setRevokedAt(Instant.now());
	}

	private RefreshSession findActive(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		RefreshSession session = refreshSessionRepository.findByTokenHash(sha256(refreshToken))
				.orElseThrow(InvalidRefreshTokenException::new);
		if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
			throw new InvalidRefreshTokenException();
		}
		if (session.getUser().getStatus() != UserStatus.ACTIVE) {
			throw new InvalidRefreshTokenException();
		}
		return session;
	}

	private String accessToken(UUID userId) {
		Instant now = Instant.now();
		long expiresAt = now.plusSeconds(jwtProperties.getAccessTtlSeconds()).getEpochSecond();
		String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
		String payload = base64Url("{\"sub\":\"" + userId + "\",\"iat\":" + now.getEpochSecond() + ",\"exp\":" + expiresAt + "}");
		String signingInput = header + "." + payload;
		return signingInput + "." + base64Url(sign(signingInput));
	}

	private String newRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private byte[] sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(jwtSecret, HMAC_SHA256));
			return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to sign access token", ex);
		}
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to hash refresh token", ex);
		}
	}

	private static String base64Url(String value) {
		return base64Url(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String base64Url(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

}
