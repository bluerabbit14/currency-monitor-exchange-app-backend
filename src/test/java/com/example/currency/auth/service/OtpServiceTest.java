package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpVerifyResponse;
import com.example.currency.auth.email.OtpEmailSender;
import com.example.currency.auth.exception.EmailDeliveryException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import com.example.currency.common.RedisHashStore;
import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.service.SystemLogEvent;
import com.example.currency.systemlog.service.SystemLogService;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final String EMAIL = "user@gmail.com";
	private static final String KEY = "otp:email:" + EMAIL;

	@Mock
	private UserRepository userRepository;

	@Mock
	private OtpEmailSender otpEmailSender;

	@Mock
	private UserAuthProviderRepository userAuthProviderRepository;

	@Mock
	private EmailUserRegistrar emailUserRegistrar;

	@Mock
	private TokenService tokenService;

	@Mock
	private RedisHashStore store;

	@Mock
	private SystemLogService systemLog;

	private OtpHasher otpHasher;

	private OtpService otpService;

	@BeforeEach
	void setUp() {
		OtpProperties properties = new OtpProperties();
		properties.setTtlSeconds(300);
		properties.setMaxAttempts(5);
		properties.setPepper("test-otp-pepper");
		otpHasher = new OtpHasher(properties);
		otpService = new OtpService(
				new ContactIdentifierService(),
				userRepository,
				otpEmailSender,
				userAuthProviderRepository,
				emailUserRegistrar,
				otpHasher,
				properties,
				tokenService,
				store,
				systemLog);
	}

	@Test
	void sendStoresHashedOtpInRedisWithTtlAndDeliversIt() {
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

		otpService.sendOtp("User@Gmail.com");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(store).create(eq(KEY), fields.capture(), ttl.capture());
		ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
		verify(otpEmailSender).sendOtp(eq(EMAIL), otp.capture());

		assertEquals(Duration.ofSeconds(300), ttl.getValue());
		assertEquals("SIGN_UP", fields.getValue().get("purpose"));
		assertEquals("0", fields.getValue().get("attempts"));
		assertNotEquals(otp.getValue(), fields.getValue().get("hash"));
		assertTrue(otp.getValue().matches("\\d{6}"));
		assertTrue(otpHasher.matches(otp.getValue(), fields.getValue().get("hash")));
		assertLogged(SystemEventType.OTP_SENT);
	}

	@Test
	void sendMarksExistingActiveUserAsSignIn() {
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(activeUser()));

		otpService.sendOtp(EMAIL);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
		verify(store).create(eq(KEY), fields.capture(), any());
		assertEquals("SIGN_IN", fields.getValue().get("purpose"));
	}

	@Test
	void sendRemovesChallengeWhenDeliveryFails() {
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		doThrow(new EmailDeliveryException()).when(otpEmailSender).sendOtp(eq(EMAIL), anyString());

		assertThrows(EmailDeliveryException.class, () -> otpService.sendOtp(EMAIL));

		verify(store).delete(KEY);
		assertLogged(SystemEventType.OTP_SEND_FAILED);
	}

	@Test
	void sendSkipsSuspendedUser() {
		User user = activeUser();
		user.setStatus(UserStatus.SUSPENDED);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

		otpService.sendOtp(EMAIL);

		verify(store, never()).create(anyString(), any(), any());
		verify(otpEmailSender, never()).sendOtp(anyString(), anyString());
		assertLogged(SystemEventType.OTP_SEND_SKIPPED);
	}

	@Test
	void sendRejectsPhone() {
		assertThrows(PhoneOtpNotSupportedException.class, () -> otpService.sendOtp("+919876543210"));
		verify(otpEmailSender, never()).sendOtp(anyString(), anyString());
	}

	@Test
	void verifyNewEmailCreatesUserOnlyAfterOtpIsValid() {
		Map<String, String> stored = challenge("123456", 0);
		User created = activeUser();
		when(store.get(KEY)).thenReturn(Optional.of(stored));
		when(store.delete(KEY)).thenReturn(true);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		when(emailUserRegistrar.register(EMAIL)).thenReturn(created);
		when(userRepository.save(created)).thenReturn(created);
		when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL))
				.thenReturn(Optional.of(new UserAuthProvider()));
		when(tokenService.issue(created)).thenReturn(new AuthTokenResponse("access", "refresh", "Bearer", 900, null));
		OtpVerifyResponse response = otpService.verifyOtp(EMAIL, "123456");

		assertTrue(response.newUser());
		assertEquals("access", response.accessToken());
		verify(emailUserRegistrar).register(EMAIL);
		assertLogged(SystemEventType.REGISTRATION_COMPLETED);
	}

	@Test
	void verifyWrongOtpNeverCreatesUser() {
		when(store.get(KEY)).thenReturn(Optional.of(challenge("123456", 0)));
		when(store.incrementIfExists(KEY, "attempts")).thenReturn(1L);

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "999999"));

		verify(emailUserRegistrar, never()).register(anyString());
	}

	@Test
	void verifyExistingUserIssuesTokensAndConsumesOtp() {
		User user = activeUser();
		UserAuthProvider provider = new UserAuthProvider();
		when(store.get(KEY)).thenReturn(Optional.of(challenge("123456", 0)));
		when(store.delete(KEY)).thenReturn(true);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(userRepository.save(user)).thenReturn(user);
		when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL))
				.thenReturn(Optional.of(provider));
		when(tokenService.issue(user)).thenReturn(new AuthTokenResponse("access", "refresh", "Bearer", 900, null));

		OtpVerifyResponse response = otpService.verifyOtp(EMAIL, "123456");

		assertFalse(response.newUser());
		assertEquals("access", response.accessToken());
		assertNotNull(user.getLastLoginAt());
		assertNotNull(user.getEmailVerifiedAt());
		assertNotNull(provider.getLastUsedAt());
		verify(store).delete(KEY);
	}

	@Test
	void verifyRejectsWrongOtpAndCountsAttempt() {
		when(store.get(KEY)).thenReturn(Optional.of(challenge("123456", 0)));
		when(store.incrementIfExists(KEY, "attempts")).thenReturn(1L);

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "000000"));

		verify(store, never()).delete(KEY);
		verify(tokenService, never()).issue(any());
		assertLogged(SystemEventType.OTP_VERIFY_FAILED);
	}

	@Test
	void verifyInvalidatesChallengeOnLastWrongAttempt() {
		when(store.get(KEY)).thenReturn(Optional.of(challenge("123456", 4)));
		when(store.incrementIfExists(KEY, "attempts")).thenReturn(5L);

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "000000"));

		verify(store).delete(KEY);
		assertLogged(SystemEventType.OTP_ATTEMPTS_EXCEEDED);
	}

	@Test
	void verifyRejectsMissingOrExpiredOtp() {
		when(store.get(KEY)).thenReturn(Optional.empty());

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "123456"));

		verify(userRepository, never()).save(any());
	}

	@Test
	void verifyRejectsReplayWhenAnotherRequestConsumedTheOtp() {
		when(store.get(KEY)).thenReturn(Optional.of(challenge("123456", 0)));
		when(store.delete(KEY)).thenReturn(false);

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "123456"));

		verify(tokenService, never()).issue(any());
	}

	private void assertLogged(SystemEventType type) {
		ArgumentCaptor<SystemLogEvent.Builder> logged = ArgumentCaptor.forClass(SystemLogEvent.Builder.class);
		verify(systemLog, org.mockito.Mockito.atLeastOnce()).log(logged.capture());
		assertTrue(logged.getAllValues().stream().anyMatch(b -> b.build().type() == type),
				"expected system log " + type);
	}

	private Map<String, String> challenge(String otp, int attempts) {
		return Map.of(
				"hash", otpHasher.hash(otp),
				"purpose", "SIGN_UP",
				"attempts", String.valueOf(attempts));
	}

	private static User activeUser() {
		User user = new User();
		user.setEmail(EMAIL);
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
