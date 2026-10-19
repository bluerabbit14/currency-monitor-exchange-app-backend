package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.GoogleOidcResponse;
import com.example.currency.auth.email.OtpEmailSender;
import com.example.currency.auth.exception.AccountNotActiveException;
import com.example.currency.auth.exception.EmailDeliveryException;
import com.example.currency.auth.exception.GoogleAccountConflictException;
import com.example.currency.auth.exception.GoogleEmailNotVerifiedException;
import com.example.currency.auth.exception.InvalidGoogleTokenException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.oidc.GoogleIdTokenValidator;
import com.example.currency.auth.oidc.VerifiedGoogleIdentity;
import com.example.currency.common.RedisHashStore;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleOidcServiceTest {

	private static final String SUB = "google-sub-1";
	private static final String EMAIL = "person@example.com";

	@Mock
	private GoogleIdTokenValidator validator;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserAuthProviderRepository providerRepository;

	@Mock
	private GoogleAccountStore accountStore;

	@Mock
	private TokenService tokenService;

	@Mock
	private OtpEmailSender emailSender;

	@Mock
	private SystemLogService systemLog;

	private InMemoryStore store;
	private OtpProperties otpProperties;
	private OtpHasher hasher;
	private GoogleOidcService service;

	@BeforeEach
	void setUp() {
		otpProperties = new OtpProperties();
		otpProperties.setPepper("test-pepper");
		otpProperties.setMaxAttempts(3);
		otpProperties.setTtlSeconds(300);
		hasher = new OtpHasher(otpProperties);
		store = new InMemoryStore();
		service = new GoogleOidcService(validator, userRepository, providerRepository, accountStore, tokenService,
				hasher, otpProperties, emailSender, store, systemLog);
		when(validator.verify(anyString())).thenReturn(identity(SUB, "Person@Example.com", true));
		when(providerRepository.findByProviderAndProviderUserId(any(), anyString())).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(tokenService.issue(any(User.class))).thenReturn(new AuthTokenResponse("access", "refresh", "Bearer", 900, null));
	}

	// ----- Google identity already linked -----

	@Test
	void signsInTheAccountAlreadyLinkedToTheGoogleSubjectEvenIfItsEmailChanged() {
		User user = user("old-address@example.com", UserStatus.ACTIVE);
		UserAuthProvider link = link(user, SUB);
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.of(link));

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, response.status());
		assertEquals("access", response.accessToken());
		assertEquals("refresh", response.refreshToken());
		assertFalse(response.newUser());
		assertNotNull(link.getLastUsedAt());
		verify(tokenService).issue(user);
		verify(accountStore, never()).register(any(), anyString());
		verify(accountStore, never()).link(any(), anyString());
		verify(emailSender, never()).sendOtp(anyString(), anyString());
		verify(userRepository, never()).findByEmailIgnoreCase(anyString());
	}

	@Test
	void signingInRefreshesTheDisplayNameAndPictureGoogleSent() {
		User user = user(EMAIL, UserStatus.ACTIVE);
		user.setDisplayName("Old Name");
		user.setProfileImageUrl("https://example.com/old.png");
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.of(link(user, SUB)));

		service.login("id-token");

		assertEquals("Pat Person", user.getDisplayName());
		assertEquals("https://example.com/p.png", user.getProfileImageUrl());
	}

	@Test
	void signingInKeepsStoredProfileValuesGoogleDidNotSend() {
		when(validator.verify(anyString()))
				.thenReturn(new VerifiedGoogleIdentity(SUB, "Person@Example.com", true, null, null));
		User user = user(EMAIL, UserStatus.ACTIVE);
		user.setDisplayName("Kept Name");
		user.setProfileImageUrl("https://example.com/kept.png");
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.of(link(user, SUB)));

		service.login("id-token");

		assertEquals("Kept Name", user.getDisplayName());
		assertEquals("https://example.com/kept.png", user.getProfileImageUrl());
	}

	@Test
	void verifyingTheLinkOtpAppliesTheNameAndPictureFromTheGoogleSignIn() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		assertNull(existing.getDisplayName(), "nothing is applied before the OTP proves mailbox ownership");
		assertNull(existing.getProfileImageUrl());

		service.verifyLink(started.linkToken(), sentOtp());

		assertEquals("Pat Person", existing.getDisplayName());
		assertEquals("https://example.com/p.png", existing.getProfileImageUrl());
	}

	@Test
	void aLinkedAccountInItsDeletionGracePeriodIsRestoredOnSignIn() {
		User user = user(EMAIL, UserStatus.ACTIVE);
		user.scheduleDeletion(java.time.Instant.now(), java.time.Instant.now().plusSeconds(86400));
		assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.of(link(user, SUB)));

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, response.status());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
	}

	@Test
	void aSuspendedLinkedAccountCannotSignIn() {
		User user = user(EMAIL, UserStatus.SUSPENDED);
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.of(link(user, SUB)));

		assertThrows(AccountNotActiveException.class, () -> service.login("id-token"));

		verifyNoInteractions(tokenService);
	}

	// ----- brand-new identity and email -----

	@Test
	void createsAnAccountForAVerifiedEmailNobodyHasUsedAndSignsIn() {
		User created = user(EMAIL, UserStatus.ACTIVE);
		when(accountStore.register(any(), anyString())).thenReturn(created);

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, response.status());
		assertTrue(response.newUser());
		ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<VerifiedGoogleIdentity> identity = ArgumentCaptor.forClass(VerifiedGoogleIdentity.class);
		verify(accountStore).register(identity.capture(), email.capture());
		assertEquals(EMAIL, email.getValue(), "the email is normalised before it is stored or looked up");
		assertEquals(SUB, identity.getValue().subject());
		verify(emailSender, never()).sendOtp(anyString(), anyString());
	}

	// ----- email already belongs to an unlinked account -----

	@Test
	void anExistingEmailStartsAnOtpFlowAndNeverLinksOrCreatesAnything() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.LINK_VERIFICATION_REQUIRED, response.status());
		assertNull(response.accessToken());
		assertNull(response.refreshToken());
		assertEquals("pe***@example.com", response.maskedEmail());
		assertEquals(300L, response.linkExpiresInSeconds());
		assertNotNull(response.linkToken());
		verify(accountStore, never()).register(any(), anyString());
		verify(accountStore, never()).link(any(), anyString());
		verifyNoInteractions(tokenService);
		// The OTP goes to the existing account's own address and is not stored in clear text.
		ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
		verify(emailSender).sendOtp(org.mockito.ArgumentMatchers.eq(EMAIL), otp.capture());
		assertTrue(store.dump().values().stream().noneMatch(fields -> fields.containsValue(otp.getValue())));
		assertTrue(store.dump().keySet().stream().noneMatch(key -> key.contains(response.linkToken())),
				"the link token is only stored hashed");
	}

	@Test
	void verifyingTheOtpLinksGoogleToThatAccountAndIssuesTokens() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");

		GoogleOidcResponse done = service.verifyLink(started.linkToken(), sentOtp());

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, done.status());
		assertFalse(done.newUser());
		assertEquals("access", done.accessToken());
		verify(accountStore).link(existing.getId(), SUB);
		verify(tokenService).issue(existing);
		verify(accountStore, never()).register(any(), anyString());
		assertTrue(store.dump().isEmpty(), "the pending link is gone once used");
	}

	@Test
	void theLinkTokenAndOtpWorkOnlyOnce() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		service.verifyLink(started.linkToken(), otp);

		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(started.linkToken(), otp));

		verify(accountStore, times(1)).link(any(), anyString());
	}

	@Test
	void aWrongOtpIsRejectedWithoutLinking() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");

		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(started.linkToken(), wrong(sentOtp())));

		verify(accountStore, never()).link(any(), anyString());
		verifyNoInteractions(tokenService);
	}

	@Test
	void tooManyWrongOtpsKillThePendingLinkEvenForTheCorrectOtpAfterwards() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();

		for (int i = 0; i < otpProperties.getMaxAttempts(); i++) {
			assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(started.linkToken(), wrong(otp)));
		}
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(started.linkToken(), otp));

		verify(accountStore, never()).link(any(), anyString());
		assertTrue(store.dump().isEmpty());
	}

	@Test
	void anExpiredOrUnknownLinkTokenIsRejected() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();

		store.expireAll();

		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(started.linkToken(), otp));
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink("made-up-token", "123456"));
		verify(accountStore, never()).link(any(), anyString());
	}

	@Test
	void theClientCannotRedirectTheLinkToAnotherAccount() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		User victim = user("victim@example.com", UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		when(userRepository.findById(victim.getId())).thenReturn(Optional.of(victim));
		GoogleOidcResponse started = service.login("id-token");

		service.verifyLink(started.linkToken(), sentOtp());

		// The only inputs are the link token and OTP; the account and subject come from the stored challenge.
		verify(accountStore).link(existing.getId(), SUB);
		verify(accountStore, never()).link(victim.getId(), SUB);
	}

	@Test
	void aSecondStartInvalidatesTheFirstPendingLink() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse first = service.login("id-token");
		String firstOtp = sentOtp();
		GoogleOidcResponse second = service.login("id-token");

		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(first.linkToken(), firstOtp));
		verify(accountStore, never()).link(any(), anyString());
		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, service.verifyLink(second.linkToken(), lastOtp()).status());
	}

	@Test
	void anEmailDeliveryFailureLeavesNoPendingLink() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		doThrow(new EmailDeliveryException()).when(emailSender).sendOtp(anyString(), anyString());

		assertThrows(EmailDeliveryException.class, () -> service.login("id-token"));

		assertTrue(store.dump().isEmpty());
		verify(accountStore, never()).link(any(), anyString());
	}

	@Test
	void anExistingAccountAlreadyLinkedToADifferentGoogleAccountIsAConflictAndSendsNoOtp() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(providerRepository.existsByUserIdAndProvider(existing.getId(), AuthProvider.GOOGLE)).thenReturn(true);

		assertThrows(GoogleAccountConflictException.class, () -> service.login("id-token"));

		verify(emailSender, never()).sendOtp(anyString(), anyString());
		assertTrue(store.dump().isEmpty());
	}

	@Test
	void aSuspendedAccountWithTheSameEmailGetsNoOtpAndNoLink() {
		User existing = user(EMAIL, UserStatus.SUSPENDED);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

		assertThrows(AccountNotActiveException.class, () -> service.login("id-token"));

		verify(emailSender, never()).sendOtp(anyString(), anyString());
	}

	@Test
	void theGoogleSubjectBeingLinkedElsewhereBeforeVerificationIsAConflict() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		User other = user("other@example.com", UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.of(link(other, SUB)));

		assertThrows(GoogleAccountConflictException.class, () -> service.verifyLink(started.linkToken(), otp));

		verify(accountStore, never()).link(any(), anyString());
		verifyNoInteractions(tokenService);
	}

	@Test
	void theAccountGainingADifferentGoogleLinkBeforeVerificationIsAConflict() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		when(providerRepository.existsByUserIdAndProvider(existing.getId(), AuthProvider.GOOGLE)).thenReturn(true);

		assertThrows(GoogleAccountConflictException.class, () -> service.verifyLink(started.linkToken(), otp));

		verify(accountStore, never()).link(any(), anyString());
	}

	@Test
	void anAccountSuspendedOrDeletedBeforeVerificationIsNotLinked() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		existing.setStatus(UserStatus.SUSPENDED);

		assertThrows(AccountNotActiveException.class, () -> service.verifyLink(started.linkToken(), otp));

		verify(accountStore, never()).link(any(), anyString());

		existing.setStatus(UserStatus.ACTIVE);
		when(userRepository.findById(existing.getId())).thenReturn(Optional.empty());
		GoogleOidcResponse again = restart(existing);
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(again.linkToken(), lastOtp()));
		verify(accountStore, never()).link(any(), anyString());
	}

	// ----- concurrency -----

	@Test
	void twoConcurrentVerificationsOfTheSameOtpLinkOnlyOnce() throws Exception {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		AtomicInteger links = new AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation -> {
			links.incrementAndGet();
			return null;
		}).when(accountStore).link(any(), anyString());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Boolean>> results = List.of(
					pool.submit(() -> attempt(go, started.linkToken(), otp)),
					pool.submit(() -> attempt(go, started.linkToken(), otp)));
			go.countDown();
			int succeeded = 0;
			for (Future<Boolean> result : results) {
				if (result.get(5, TimeUnit.SECONDS)) {
					succeeded++;
				}
			}
			assertEquals(1, succeeded);
			assertEquals(1, links.get());
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aConcurrentSignUpThatLostTheRaceSignsInTheAccountTheWinnerCreated() {
		User winner = user(EMAIL, UserStatus.ACTIVE);
		when(accountStore.register(any(), anyString())).thenThrow(new DataIntegrityViolationException("dup"));
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.empty(), Optional.of(link(winner, SUB)));

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, response.status());
		assertFalse(response.newUser());
		verify(tokenService).issue(winner);
	}

	@Test
	void aSignUpRaceWonByAnEmailOnlyAccountFallsBackToTheVerifiedLinkFlow() {
		User winner = user(EMAIL, UserStatus.ACTIVE);
		when(accountStore.register(any(), anyString())).thenThrow(new DataIntegrityViolationException("dup"));
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty(), Optional.of(winner));

		GoogleOidcResponse response = service.login("id-token");

		assertEquals(GoogleOidcResponse.Status.LINK_VERIFICATION_REQUIRED, response.status());
		verify(emailSender).sendOtp(org.mockito.ArgumentMatchers.eq(EMAIL), anyString());
		verifyNoInteractions(tokenService);
	}

	@Test
	void anUnexplainedIntegrityViolationOnSignUpIsNotSwallowed() {
		when(accountStore.register(any(), anyString())).thenThrow(new DataIntegrityViolationException("boom"));

		assertThrows(DataIntegrityViolationException.class, () -> service.login("id-token"));

		verifyNoInteractions(tokenService);
	}

	@Test
	void aLinkRaceLostToTheSameLinkIsTreatedAsSuccess() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		doThrow(new DataIntegrityViolationException("dup")).when(accountStore).link(any(), anyString());
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.empty(), Optional.of(link(existing, SUB)));

		assertEquals(GoogleOidcResponse.Status.SIGNED_IN, service.verifyLink(started.linkToken(), otp).status());
	}

	@Test
	void aLinkRaceLostToAnotherAccountIsAConflict() {
		User existing = user(EMAIL, UserStatus.ACTIVE);
		User other = user("other@example.com", UserStatus.ACTIVE);
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		doThrow(new DataIntegrityViolationException("dup")).when(accountStore).link(any(), anyString());
		GoogleOidcResponse started = service.login("id-token");
		String otp = sentOtp();
		when(providerRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
				.thenReturn(Optional.empty(), Optional.of(link(other, SUB)));

		assertThrows(GoogleAccountConflictException.class, () -> service.verifyLink(started.linkToken(), otp));
		verifyNoInteractions(tokenService);
	}

	@Test
	void aBlankOrMissingLinkTokenIsRejectedWithoutTouchingTheStore() {
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink(null, "123456"));
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink("  ", "123456"));
		assertThrows(OtpVerificationFailedException.class, () -> service.verifyLink("token", null));
		verify(accountStore, never()).link(any(), anyString());
	}


	// ----- token problems -----

	@Test
	void anUnverifiedGoogleEmailIsRejectedBeforeAnyAccountLookup() {
		when(validator.verify(anyString())).thenReturn(identity(SUB, "Person@Example.com", false));

		assertThrows(GoogleEmailNotVerifiedException.class, () -> service.login("id-token"));

		verifyNoInteractions(userRepository, accountStore, tokenService, emailSender);
		verify(providerRepository, never()).findByProviderAndProviderUserId(any(), anyString());
	}

	@Test
	void anInvalidTokenTouchesNothing() {
		when(validator.verify(anyString())).thenThrow(new InvalidGoogleTokenException());

		assertThrows(InvalidGoogleTokenException.class, () -> service.login("forged"));

		verifyNoInteractions(userRepository, accountStore, tokenService, emailSender);
		assertTrue(store.dump().isEmpty());
	}

	@Test
	void masksTheEmailForTheLinkPrompt() {
		assertEquals("jo***@gmail.com", GoogleOidcService.mask("john@gmail.com"));
		assertEquals("a***@x.com", GoogleOidcService.mask("a@x.com"));
		assertEquals("***", GoogleOidcService.mask("not-an-email"));
	}

	// ----- helpers -----

	private boolean attempt(CountDownLatch go, String linkToken, String otp) throws InterruptedException {
		go.await();
		try {
			service.verifyLink(linkToken, otp);
			return true;
		}
		catch (OtpVerificationFailedException ex) {
			return false;
		}
	}

	/** Starts a fresh link for the same Google subject and account. */
	private GoogleOidcResponse restart(User existing) {
		when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
		return service.login("id-token");
	}

	private String sentOtp() {
		return lastOtp();
	}

	private String lastOtp() {
		ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
		verify(emailSender, org.mockito.Mockito.atLeastOnce()).sendOtp(anyString(), otp.capture());
		return otp.getValue();
	}

	private static String wrong(String otp) {
		return otp.equals("000000") ? "111111" : "000000";
	}

	private static VerifiedGoogleIdentity identity(String sub, String email, boolean verified) {
		return new VerifiedGoogleIdentity(sub, email, verified, "Pat Person", "https://example.com/p.png");
	}

	private static User user(String email, UserStatus status) {
		User user = new User();
		setId(user, UUID.randomUUID());
		user.setEmail(email);
		user.setStatus(status);
		return user;
	}

	private static UserAuthProvider link(User user, String subject) {
		UserAuthProvider provider = new UserAuthProvider();
		provider.setUser(user);
		provider.setProvider(AuthProvider.GOOGLE);
		provider.setProviderUserId(subject);
		return provider;
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

	/** A thread-safe stand-in for Redis with the same single-use, increment and expiry semantics. */
	private static final class InMemoryStore extends RedisHashStore {

		private final Map<String, Map<String, String>> data = new ConcurrentHashMap<>();

		InMemoryStore() {
			super(null);
		}

		@Override
		public void create(String key, Map<String, String> fields, Duration ttl) {
			data.put(key, new ConcurrentHashMap<>(fields));
		}

		@Override
		public Optional<Map<String, String>> get(String key) {
			Map<String, String> fields = data.get(key);
			return fields == null ? Optional.empty() : Optional.of(Map.copyOf(fields));
		}

		@Override
		public boolean updateIfExists(String key, Map<String, String> fields) {
			Map<String, String> existing = data.get(key);
			if (existing == null) {
				return false;
			}
			existing.putAll(fields);
			return true;
		}

		@Override
		public long incrementIfExists(String key, String field) {
			Map<String, String> existing = data.get(key);
			if (existing == null) {
				return -1;
			}
			return Long.parseLong(existing.merge(field, "1", (old, one) -> String.valueOf(Long.parseLong(old) + 1)));
		}

		@Override
		public boolean delete(String key) {
			return data.remove(key) != null;
		}

		Map<String, Map<String, String>> dump() {
			return data;
		}

		void expireAll() {
			data.clear();
		}

	}

}
