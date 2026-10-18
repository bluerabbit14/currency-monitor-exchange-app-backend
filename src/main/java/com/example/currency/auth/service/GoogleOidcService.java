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
import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.enums.SystemLogChannel;
import com.example.currency.systemlog.enums.SystemLogStatus;
import com.example.currency.systemlog.service.SystemLogEvent;
import com.example.currency.systemlog.service.SystemLogService;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign in with Google. The Google ID token is verified (signature, issuer, audience, expiry) and then only
 * Google's stable {@code sub} identifies the person; the email is never proof of identity.
 * <ul>
 *   <li>{@code sub} already linked: sign in to that account.</li>
 *   <li>Neither {@code sub} nor email known: create the account, link {@code sub}, sign in.</li>
 *   <li>Email belongs to an account with no Google link: never merge on the email alone. An OTP is emailed to
 *       that account's own address and the pending link is held in Redis, bound to this {@code sub} and that
 *       account. Only {@link #verifyLink} with the secret link token and the OTP links and signs in.</li>
 * </ul>
 * The pending link has the OTP's lifetime, a retry limit, and is single use: the key is deleted atomically and
 * only the caller that actually removed it may link. The client never names the account to link.
 */
@Service
public class GoogleOidcService {

	private static final String LINK_PREFIX = "oidc:google:link:";
	private static final String LINK_BY_SUBJECT_PREFIX = "oidc:google:link-by-sub:";
	private static final String SUBJECT = "sub";
	private static final String USER_ID = "userId";
	private static final String EMAIL = "email";
	private static final String NAME = "name";
	private static final String PICTURE = "picture";
	private static final String HASH = "hash";
	private static final String ATTEMPTS = "attempts";
	private static final String POINTER = "key";
	private static final String PROVIDER = "RESEND";

	private final GoogleIdTokenValidator tokenValidator;
	private final UserRepository userRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;
	private final GoogleAccountStore accountStore;
	private final TokenService tokenService;
	private final OtpHasher otpHasher;
	private final OtpProperties otpProperties;
	private final OtpEmailSender otpEmailSender;
	private final RedisHashStore store;
	private final SystemLogService systemLog;
	private final SecureRandom secureRandom = new SecureRandom();

	public GoogleOidcService(
			GoogleIdTokenValidator tokenValidator,
			UserRepository userRepository,
			UserAuthProviderRepository userAuthProviderRepository,
			GoogleAccountStore accountStore,
			TokenService tokenService,
			OtpHasher otpHasher,
			OtpProperties otpProperties,
			OtpEmailSender otpEmailSender,
			RedisHashStore store,
			SystemLogService systemLog) {
		this.tokenValidator = tokenValidator;
		this.userRepository = userRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
		this.accountStore = accountStore;
		this.tokenService = tokenService;
		this.otpHasher = otpHasher;
		this.otpProperties = otpProperties;
		this.otpEmailSender = otpEmailSender;
		this.store = store;
		this.systemLog = systemLog;
	}

	@Transactional
	public GoogleOidcResponse login(String idToken) {
		VerifiedGoogleIdentity identity = verified(idToken);
		String email = identity.email().trim().toLowerCase(Locale.ROOT);

		Optional<UserAuthProvider> linked = userAuthProviderRepository
				.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.subject());
		if (linked.isPresent()) {
			linked.get().setLastUsedAt(Instant.now());
			User user = linked.get().getUser();
			GoogleAccountStore.applyProfile(user, identity.name(), identity.pictureUrl());
			return signedIn(user, false, email);
		}

		Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
		if (existing.isPresent()) {
			return startLink(existing.get(), identity);
		}

		User created;
		try {
			created = accountStore.register(identity, email);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent request created the account or the link first. Re-read and follow the normal rules.
			return afterRaceOnRegister(identity, email, ex);
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.REGISTRATION_COMPLETED, SystemLogStatus.SUCCESS)
				.userId(created.getId()).recipient(email).provider("GOOGLE"));
		return signedIn(created, true, email);
	}

	@Transactional
	public GoogleOidcResponse verifyLink(String linkToken, String otp) {
		if (linkToken == null || linkToken.isBlank() || otp == null) {
			throw linkFailed(null, null, "LINK_EXPIRED_OR_MISSING", SystemEventType.GOOGLE_LINK_FAILED);
		}
		String key = LINK_PREFIX + sha256(linkToken);
		Map<String, String> pending = store.get(key).orElse(null);
		if (pending == null) {
			throw linkFailed(null, null, "LINK_EXPIRED_OR_MISSING", SystemEventType.GOOGLE_LINK_FAILED);
		}
		String email = pending.get(EMAIL);
		if (attempts(pending) >= otpProperties.getMaxAttempts()) {
			discard(key, pending);
			throw linkFailed(null, email, "LINK_ATTEMPTS_EXCEEDED", SystemEventType.OTP_ATTEMPTS_EXCEEDED);
		}
		if (!otpHasher.matches(otp, pending.get(HASH))) {
			long attempts = store.incrementIfExists(key, ATTEMPTS);
			if (attempts >= otpProperties.getMaxAttempts()) {
				discard(key, pending);
				throw linkFailed(null, email, "LINK_ATTEMPTS_EXCEEDED", SystemEventType.OTP_ATTEMPTS_EXCEEDED);
			}
			throw linkFailed(null, email, "LINK_OTP_MISMATCH", SystemEventType.OTP_VERIFY_FAILED);
		}
		// Single use: only the request that actually removes the key may link.
		if (!store.delete(key)) {
			throw linkFailed(null, email, "LINK_ALREADY_USED", SystemEventType.GOOGLE_LINK_FAILED);
		}
		String subject = pending.get(SUBJECT);
		if (subject != null) {
			store.delete(LINK_BY_SUBJECT_PREFIX + sha256(subject));
		}

		UUID userId = parseUuid(pending.get(USER_ID));
		User user = userId == null ? null : userRepository.findById(userId).orElse(null);
		if (user == null || subject == null || subject.isBlank()) {
			throw linkFailed(userId, email, "LINK_ACCOUNT_GONE", SystemEventType.GOOGLE_LINK_FAILED);
		}
		requireMayAuthenticate(user);

		// The identity or the account may have been linked since the OTP was sent; never replace or re-point a link.
		Optional<UserAuthProvider> bySubject = userAuthProviderRepository
				.findByProviderAndProviderUserId(AuthProvider.GOOGLE, subject);
		if (bySubject.isPresent() && !bySubject.get().getUser().getId().equals(user.getId())) {
			throw conflict(user, email, "This Google account is already linked to another account");
		}
		if (bySubject.isEmpty()) {
			if (userAuthProviderRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
				throw conflict(user, email, "This account is already linked to a different Google account");
			}
			try {
				accountStore.link(user.getId(), subject);
			}
			catch (DataIntegrityViolationException ex) {
				Optional<UserAuthProvider> now = userAuthProviderRepository
						.findByProviderAndProviderUserId(AuthProvider.GOOGLE, subject);
				if (now.isEmpty() || !now.get().getUser().getId().equals(user.getId())) {
					throw conflict(user, email, "This Google account could not be linked");
				}
				// A concurrent verification of the same link won: this one is equivalent.
			}
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_LINKED, SystemLogStatus.SUCCESS)
				.channel(SystemLogChannel.EMAIL).userId(user.getId()).recipient(email).provider("GOOGLE"));
		// Control of the mailbox was just proven by the OTP.
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(Instant.now());
		}
		GoogleAccountStore.applyProfile(user, pending.get(NAME), pending.get(PICTURE));
		return signedIn(user, false, email);
	}

	private VerifiedGoogleIdentity verified(String idToken) {
		VerifiedGoogleIdentity identity;
		try {
			identity = tokenValidator.verify(idToken);
		}
		catch (InvalidGoogleTokenException ex) {
			systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_TOKEN_REJECTED, SystemLogStatus.FAILURE)
					.provider("GOOGLE").error("INVALID_GOOGLE_TOKEN", null));
			throw ex;
		}
		if (!identity.emailVerified()) {
			systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_TOKEN_REJECTED, SystemLogStatus.FAILURE)
					.provider("GOOGLE").error(GoogleEmailNotVerifiedException.CODE, null));
			throw new GoogleEmailNotVerifiedException();
		}
		return identity;
	}

	private GoogleOidcResponse afterRaceOnRegister(
			VerifiedGoogleIdentity identity, String email, DataIntegrityViolationException original) {
		Optional<UserAuthProvider> linked = userAuthProviderRepository
				.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.subject());
		if (linked.isPresent()) {
			linked.get().setLastUsedAt(Instant.now());
			return signedIn(linked.get().getUser(), false, email);
		}
		Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
		if (existing.isPresent()) {
			return startLink(existing.get(), identity);
		}
		throw original;
	}

	private GoogleOidcResponse startLink(User existing, VerifiedGoogleIdentity identity) {
		requireMayAuthenticate(existing);
		if (userAuthProviderRepository.existsByUserIdAndProvider(existing.getId(), AuthProvider.GOOGLE)) {
			// Linked to a different sub (this sub is not linked, or we would have signed in): do not add a second.
			throw conflict(existing, existing.getEmail(),
					"This account is already linked to a different Google account");
		}
		String email = existing.getEmail();
		String linkToken = newLinkToken();
		String key = LINK_PREFIX + sha256(linkToken);
		String pointerKey = LINK_BY_SUBJECT_PREFIX + sha256(identity.subject());
		String otp = generateOtp();

		// One pending link per Google identity: a new start invalidates the previous one.
		store.get(pointerKey).map(pointer -> pointer.get(POINTER)).ifPresent(store::delete);
		Duration ttl = Duration.ofSeconds(otpProperties.getTtlSeconds());
		// Name and picture ride along so verifyLink can apply them once the OTP proves mailbox ownership.
		Map<String, String> pending = new HashMap<>();
		pending.put(SUBJECT, identity.subject());
		pending.put(USER_ID, existing.getId().toString());
		pending.put(EMAIL, email);
		pending.put(HASH, otpHasher.hash(otp));
		pending.put(ATTEMPTS, "0");
		if (identity.name() != null) {
			pending.put(NAME, identity.name());
		}
		if (identity.pictureUrl() != null) {
			pending.put(PICTURE, identity.pictureUrl());
		}
		store.create(key, pending, ttl);
		store.create(pointerKey, Map.of(POINTER, key), ttl);
		try {
			otpEmailSender.sendOtp(email, otp);
		}
		catch (EmailDeliveryException ex) {
			store.delete(key);
			store.delete(pointerKey);
			systemLog.log(SystemLogEvent.of(SystemEventType.OTP_SEND_FAILED, SystemLogStatus.FAILURE)
					.channel(SystemLogChannel.EMAIL).userId(existing.getId()).recipient(email).provider(PROVIDER)
					.error("EMAIL_DELIVERY_FAILED", ex.getMessage()).meta("purpose", "LINK_GOOGLE"));
			throw ex;
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_LINK_REQUIRED, SystemLogStatus.SUCCESS)
				.channel(SystemLogChannel.EMAIL).userId(existing.getId()).recipient(email).provider(PROVIDER)
				.meta("ttlSeconds", otpProperties.getTtlSeconds()));
		return GoogleOidcResponse.linkRequired(linkToken, mask(email), otpProperties.getTtlSeconds());
	}

	private GoogleOidcResponse signedIn(User user, boolean newUser, String email) {
		requireMayAuthenticate(user);
		boolean restored = false;
		if (user.getStatus() == UserStatus.PENDING_DELETION) {
			user.cancelDeletion();
			restored = true;
		}
		user.setLastLoginAt(Instant.now());
		user = userRepository.save(user);
		AuthTokenResponse tokens = tokenService.issue(user);
		if (restored) {
			systemLog.log(SystemLogEvent.of(SystemEventType.ACCOUNT_DELETION_CANCELLED, SystemLogStatus.SUCCESS)
					.userId(user.getId()).recipient(email));
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.LOGIN_SUCCESS, SystemLogStatus.SUCCESS)
				.userId(user.getId()).recipient(email).provider("GOOGLE"));
		return GoogleOidcResponse.signedIn(tokens, newUser);
	}

	/** Suspended and deleted accounts cannot sign in or be linked; one in its deletion grace period can. */
	private void requireMayAuthenticate(User user) {
		if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_DELETION) {
			systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_LINK_FAILED, SystemLogStatus.FAILURE)
					.userId(user.getId()).provider("GOOGLE").error(AccountNotActiveException.CODE, null));
			throw new AccountNotActiveException();
		}
	}

	private GoogleAccountConflictException conflict(User user, String email, String message) {
		systemLog.log(SystemLogEvent.of(SystemEventType.GOOGLE_LINK_FAILED, SystemLogStatus.FAILURE)
				.userId(user.getId()).recipient(email).provider("GOOGLE")
				.error(GoogleAccountConflictException.CODE, message));
		return new GoogleAccountConflictException(message);
	}

	private OtpVerificationFailedException linkFailed(UUID userId, String email, String reason, SystemEventType type) {
		systemLog.log(SystemLogEvent.of(type, SystemLogStatus.FAILURE)
				.channel(SystemLogChannel.EMAIL).userId(userId).recipient(email).error(reason, null));
		return new OtpVerificationFailedException();
	}

	private void discard(String key, Map<String, String> pending) {
		store.delete(key);
		String subject = pending.get(SUBJECT);
		if (subject != null) {
			store.delete(LINK_BY_SUBJECT_PREFIX + sha256(subject));
		}
	}

	private static int attempts(Map<String, String> pending) {
		try {
			return Integer.parseInt(pending.getOrDefault(ATTEMPTS, "0"));
		}
		catch (NumberFormatException ex) {
			return Integer.MAX_VALUE;
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return value == null ? null : UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** {@code jo***@gmail.com}: enough for the user to recognise the mailbox, not enough to disclose it. */
	static String mask(String email) {
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		String local = email.substring(0, at);
		return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
	}

	private String newLinkToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String generateOtp() {
		return String.valueOf(100000 + secureRandom.nextInt(900000));
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to hash value", ex);
		}
	}

}
