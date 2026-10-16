package com.example.currency.auth.service;

import com.example.currency.auth.config.JwtProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.entity.RefreshSession;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.exception.InvalidRefreshTokenException;
import com.example.currency.auth.repository.RefreshSessionRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

	@Mock
	private RefreshSessionRepository refreshSessionRepository;

	@Mock
	private UserRepository userRepository;

	private TokenService tokenService;

	private final Map<String, RefreshSession> sessions = new HashMap<>();

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("test-jwt-secret");
		properties.setAccessTtlSeconds(900);
		properties.setRefreshTtlSeconds(2_592_000);
		tokenService = new TokenService(refreshSessionRepository, userRepository, properties);
		lenient().when(userRepository.existsByIdAndStatus(any(), eq(UserStatus.ACTIVE))).thenReturn(true);
		lenient().when(refreshSessionRepository.save(any(RefreshSession.class))).thenAnswer(invocation -> {
			RefreshSession session = invocation.getArgument(0);
			sessions.put(session.getTokenHash(), session);
			return session;
		});
		lenient().when(refreshSessionRepository.findByTokenHash(any())).thenAnswer(invocation ->
				Optional.ofNullable(sessions.get(invocation.getArgument(0))));
	}

	@Test
	void issueStoresOnlyTheRefreshTokenHash() {
		AuthTokenResponse response = tokenService.issue(activeUser());

		assertEquals("Bearer", response.tokenType());
		assertEquals(900, response.expiresInSeconds());
		assertEquals(3, response.accessToken().split("\\.").length);
		assertEquals(1, sessions.size());
		RefreshSession session = sessions.values().iterator().next();
		assertNotEquals(response.refreshToken(), session.getTokenHash());
		assertTrue(session.getExpiresAt().isAfter(Instant.now().plusSeconds(2_000_000)));
	}

	@Test
	void refreshRotatesTheSession() {
		AuthTokenResponse first = tokenService.issue(activeUser());
		RefreshSession original = sessions.values().iterator().next();

		AuthTokenResponse second = tokenService.refresh(first.refreshToken());

		assertNotNull(original.getRevokedAt());
		assertNotEquals(first.refreshToken(), second.refreshToken());
		assertEquals(2, sessions.size());
	}

	@Test
	void logoutRevokesTheSession() {
		AuthTokenResponse issued = tokenService.issue(activeUser());

		tokenService.logout(issued.refreshToken());

		assertNotNull(sessions.values().iterator().next().getRevokedAt());
		assertThrows(InvalidRefreshTokenException.class, () -> tokenService.logout(issued.refreshToken()));
	}

	@Test
	void rejectsUnknownRefreshToken() {
		assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh("missing"));
	}

	@Test
	void requireUserIdReturnsTheSubject() {
		User user = activeUser();
		AuthTokenResponse issued = tokenService.issue(user);

		assertEquals(user.getId(), tokenService.requireUserId("Bearer " + issued.accessToken()));
	}

	@Test
	void requireUserIdRejectsATamperedSignature() {
		AuthTokenResponse issued = tokenService.issue(activeUser());
		String token = issued.accessToken();
		char last = token.charAt(token.length() - 1);
		String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

		assertThrows(InvalidAccessTokenException.class, () -> tokenService.requireUserId("Bearer " + tampered));
	}

	@Test
	void requireUserIdRejectsAnExpiredToken() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("test-jwt-secret");
		properties.setAccessTtlSeconds(-1);
		properties.setRefreshTtlSeconds(2_592_000);
		TokenService expiredTokens = new TokenService(refreshSessionRepository, userRepository, properties);
		AuthTokenResponse issued = expiredTokens.issue(activeUser());

		assertThrows(InvalidAccessTokenException.class,
				() -> expiredTokens.requireUserId("Bearer " + issued.accessToken()));
	}

	@Test
	void requireUserIdRejectsAMissingBearerPrefix() {
		AuthTokenResponse issued = tokenService.issue(activeUser());

		assertThrows(InvalidAccessTokenException.class, () -> tokenService.requireUserId(issued.accessToken()));
		assertThrows(InvalidAccessTokenException.class, () -> tokenService.requireUserId(null));
	}

	private static User activeUser() {
		User user = new User();
		user.setStatus(UserStatus.ACTIVE);
		try {
			var field = User.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, UUID.randomUUID());
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		return user;
	}

}
