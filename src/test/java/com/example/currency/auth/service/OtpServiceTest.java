package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.email.OtpEmailSender;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import com.example.currency.user.entity.OtpVerification;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.OtpIdentifierType;
import com.example.currency.user.enums.OtpPurpose;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.OtpVerificationRepository;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final String EMAIL = "user@gmail.com";

	@Mock
	private UserRepository userRepository;

	@Mock
	private OtpEmailSender otpEmailSender;

	@Mock
	private OtpVerificationRepository otpRepository;

	@Mock
	private UserAuthProviderRepository userAuthProviderRepository;

	@Mock
	private EmailUserRegistrar emailUserRegistrar;

	@Mock
	private TokenService tokenService;

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
				otpRepository,
				userAuthProviderRepository,
				emailUserRegistrar,
				otpHasher,
				properties,
				tokenService);
	}

	@Test
	void sendStoresHashAndDeliversSignupOtp() {
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		when(otpRepository.findByIdentifierAndIdentifierTypeAndVerifiedAtIsNull(EMAIL, OtpIdentifierType.EMAIL))
				.thenReturn(List.of());

		otpService.sendOtp("User@Gmail.com");

		ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
		verify(otpRepository).save(saved.capture());
		ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
		verify(otpEmailSender).sendOtp(eq(EMAIL), otp.capture());

		OtpVerification verification = saved.getValue();
		assertNull(verification.getUser());
		assertEquals(OtpPurpose.SIGN_UP, verification.getPurpose());
		assertEquals(OtpIdentifierType.EMAIL, verification.getIdentifierType());
		assertEquals(EMAIL, verification.getIdentifier());
		assertEquals(0, verification.getAttemptCount());
		assertTrue(verification.getExpiresAt().isAfter(Instant.now().plusSeconds(240)));
		assertNotEquals(otp.getValue(), verification.getOtpHash());
		assertTrue(otp.getValue().matches("\\d{6}"));
		assertTrue(otpHasher.matches(otp.getValue(), verification.getOtpHash()));
	}

	@Test
	void sendLinksActiveUserAsSignIn() {
		User user = activeUser();
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(otpRepository.findByIdentifierAndIdentifierTypeAndVerifiedAtIsNull(EMAIL, OtpIdentifierType.EMAIL))
				.thenReturn(List.of());

		otpService.sendOtp(EMAIL);

		ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
		verify(otpRepository).save(saved.capture());
		assertEquals(user, saved.getValue().getUser());
		assertEquals(OtpPurpose.SIGN_IN, saved.getValue().getPurpose());
	}

	@Test
	void sendExpiresOlderChallenges() {
		OtpVerification previous = challenge("000000");
		previous.setExpiresAt(Instant.now().plusSeconds(200));
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		when(otpRepository.findByIdentifierAndIdentifierTypeAndVerifiedAtIsNull(EMAIL, OtpIdentifierType.EMAIL))
				.thenReturn(List.of(previous));

		otpService.sendOtp(EMAIL);

		assertTrue(previous.getExpiresAt().isBefore(Instant.now().plusSeconds(2)));
	}

	@Test
	void sendSkipsSuspendedUser() {
		User user = activeUser();
		user.setStatus(UserStatus.SUSPENDED);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

		otpService.sendOtp(EMAIL);

		verify(otpRepository, never()).save(any());
		verify(otpEmailSender, never()).sendOtp(anyString(), anyString());
	}

	@Test
	void sendRejectsPhone() {
		assertThrows(PhoneOtpNotSupportedException.class, () -> otpService.sendOtp("+919876543210"));
		verify(otpEmailSender, never()).sendOtp(anyString(), anyString());
	}

	@Test
	void verifySignupIssuesTokensAndConsumesOtp() {
		OtpVerification challenge = challenge("123456");
		User created = activeUser();
		UserAuthProvider provider = new UserAuthProvider();
		when(otpRepository.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
				EMAIL,
				OtpIdentifierType.EMAIL)).thenReturn(Optional.of(challenge));
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		when(emailUserRegistrar.register(EMAIL)).thenReturn(created);
		when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL))
				.thenReturn(Optional.of(provider));
		when(userRepository.save(created)).thenReturn(created);
		when(tokenService.issue(created)).thenReturn(new AuthTokenResponse("access", "refresh", "Bearer", 900));

		AuthTokenResponse response = otpService.verifyOtp(EMAIL, "123456");

		assertEquals("access", response.accessToken());
		assertNotNull(challenge.getVerifiedAt());
		assertEquals(created, challenge.getUser());
		assertNotNull(created.getLastLoginAt());
		assertNotNull(provider.getLastUsedAt());
	}

	@Test
	void verifyReloadsUserWhenSignupRaces() {
		OtpVerification challenge = challenge("123456");
		User existing = activeUser();
		when(otpRepository.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
				EMAIL,
				OtpIdentifierType.EMAIL)).thenReturn(Optional.of(challenge));
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty(), Optional.of(existing));
		when(emailUserRegistrar.register(EMAIL)).thenThrow(new DataIntegrityViolationException("duplicate email"));
		when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL))
				.thenReturn(Optional.of(new UserAuthProvider()));
		when(userRepository.save(existing)).thenReturn(existing);
		when(tokenService.issue(existing)).thenReturn(new AuthTokenResponse("access", "refresh", "Bearer", 900));

		AuthTokenResponse response = otpService.verifyOtp(EMAIL, "123456");

		assertEquals("access", response.accessToken());
		assertEquals(existing, challenge.getUser());
	}

	@Test
	void verifyRejectsWrongOtpAndIncrementsAttempts() {
		OtpVerification challenge = challenge("123456");
		when(otpRepository.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
				EMAIL,
				OtpIdentifierType.EMAIL)).thenReturn(Optional.of(challenge));

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "000000"));

		assertEquals(1, challenge.getAttemptCount());
		assertNull(challenge.getVerifiedAt());
		verify(tokenService, never()).issue(any());
	}

	@Test
	void verifyRejectsExpiredOtp() {
		OtpVerification challenge = challenge("123456");
		challenge.setExpiresAt(Instant.now().minusSeconds(1));
		when(otpRepository.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
				EMAIL,
				OtpIdentifierType.EMAIL)).thenReturn(Optional.of(challenge));

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "123456"));
		assertEquals(0, challenge.getAttemptCount());
	}

	@Test
	void verifyRejectsExhaustedAttempts() {
		OtpVerification challenge = challenge("123456");
		challenge.setAttemptCount(5);
		when(otpRepository.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
				EMAIL,
				OtpIdentifierType.EMAIL)).thenReturn(Optional.of(challenge));

		assertThrows(OtpVerificationFailedException.class, () -> otpService.verifyOtp(EMAIL, "123456"));
		assertEquals(5, challenge.getAttemptCount());
		assertNull(challenge.getVerifiedAt());
	}

	private OtpVerification challenge(String otp) {
		OtpVerification verification = new OtpVerification();
		verification.setIdentifier(EMAIL);
		verification.setIdentifierType(OtpIdentifierType.EMAIL);
		verification.setPurpose(OtpPurpose.SIGN_UP);
		verification.setOtpHash(otpHasher.hash(otp));
		verification.setExpiresAt(Instant.now().plusSeconds(300));
		verification.setAttemptCount(0);
		return verification;
	}

	private static User activeUser() {
		User user = new User();
		user.setEmail(EMAIL);
		user.setStatus(UserStatus.ACTIVE);
		setId(user, UUID.randomUUID());
		return user;
	}

	private static void setId(User user, UUID id) {
		try {
			var field = User.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, id);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
