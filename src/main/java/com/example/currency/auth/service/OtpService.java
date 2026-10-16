package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyResponse;
import com.example.currency.auth.email.OtpEmailSender;
import com.example.currency.auth.exception.EmailDeliveryException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import com.example.currency.auth.service.ContactIdentifierService.IdentifiedContact;
import com.example.currency.common.RedisHashStore;
import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.enums.SystemLogChannel;
import com.example.currency.systemlog.enums.SystemLogStatus;
import com.example.currency.systemlog.service.SystemLogEvent;
import com.example.currency.systemlog.service.SystemLogService;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.OtpIdentifierType;
import com.example.currency.user.enums.OtpPurpose;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The OTP challenge lives in Redis only; the key TTL is the OTP expiry.
 * A new user row is written to the database exclusively after the OTP is verified.
 */
@Service
public class OtpService {

	private static final String KEY_PREFIX = "otp:email:";
	private static final String HASH = "hash";
	private static final String PURPOSE = "purpose";
	private static final String ATTEMPTS = "attempts";
	private static final String PROVIDER = "RESEND";

	private final ContactIdentifierService contactIdentifierService;
	private final UserRepository userRepository;
	private final OtpEmailSender otpEmailSender;
	private final UserAuthProviderRepository userAuthProviderRepository;
	private final EmailUserRegistrar emailUserRegistrar;
	private final OtpHasher otpHasher;
	private final OtpProperties otpProperties;
	private final TokenService tokenService;
	private final RedisHashStore store;
	private final SystemLogService systemLog;
	private final SecureRandom secureRandom = new SecureRandom();

	public OtpService(
			ContactIdentifierService contactIdentifierService,
			UserRepository userRepository,
			OtpEmailSender otpEmailSender,
			UserAuthProviderRepository userAuthProviderRepository,
			EmailUserRegistrar emailUserRegistrar,
			OtpHasher otpHasher,
			OtpProperties otpProperties,
			TokenService tokenService,
			RedisHashStore store,
			SystemLogService systemLog) {
		this.contactIdentifierService = contactIdentifierService;
		this.userRepository = userRepository;
		this.otpEmailSender = otpEmailSender;
		this.userAuthProviderRepository = userAuthProviderRepository;
		this.emailUserRegistrar = emailUserRegistrar;
		this.otpHasher = otpHasher;
		this.otpProperties = otpProperties;
		this.tokenService = tokenService;
		this.store = store;
		this.systemLog = systemLog;
	}

	public OtpSendResponse sendOtp(String contact) {
		IdentifiedContact identified = contactIdentifierService.identify(contact);
		if (identified.type() == OtpIdentifierType.PHONE) {
			throw new PhoneOtpNotSupportedException();
		}
		sendEmailOtp(identified.value());
		return new OtpSendResponse(identified.value(), otpProperties.getTtlSeconds());
	}

	@Transactional
	public OtpVerifyResponse verifyOtp(String contact, String otp) {
		IdentifiedContact identified = contactIdentifierService.identify(contact);
		if (identified.type() == OtpIdentifierType.PHONE) {
			throw new PhoneOtpNotSupportedException();
		}
		String email = identified.value();
		String key = key(email);

		Map<String, String> challenge = store.get(key).orElse(null);
		if (challenge == null) {
			throw failed(email, "OTP_EXPIRED_OR_MISSING", SystemEventType.OTP_VERIFY_FAILED);
		}
		if (attempts(challenge) >= otpProperties.getMaxAttempts()) {
			store.delete(key);
			throw failed(email, "OTP_ATTEMPTS_EXCEEDED", SystemEventType.OTP_ATTEMPTS_EXCEEDED);
		}
		if (!otpHasher.matches(otp, challenge.get(HASH))) {
			long attempts = store.incrementIfExists(key, ATTEMPTS);
			if (attempts >= otpProperties.getMaxAttempts()) {
				store.delete(key);
				throw failed(email, "OTP_ATTEMPTS_EXCEEDED", SystemEventType.OTP_ATTEMPTS_EXCEEDED);
			}
			throw failed(email, "OTP_MISMATCH", SystemEventType.OTP_VERIFY_FAILED);
		}
		// Single use: only the request that actually removes the key may proceed.
		if (!store.delete(key)) {
			throw failed(email, "OTP_ALREADY_USED", SystemEventType.OTP_VERIFY_FAILED);
		}

		User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		boolean newUser = false;
		if (user == null) {
			user = register(email);
			newUser = true;
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw failed(email, "USER_NOT_ACTIVE", SystemEventType.OTP_VERIFY_FAILED);
		}

		Instant now = Instant.now();
		user.setLastLoginAt(now);
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(now);
		}
		user = userRepository.save(user);
		touchProvider(user, email);

		systemLog.log(SystemLogEvent.of(SystemEventType.OTP_VERIFIED, SystemLogStatus.SUCCESS)
				.channel(SystemLogChannel.EMAIL)
				.userId(user.getId())
				.recipient(email)
				.meta("purpose", challenge.get(PURPOSE)));
		if (newUser) {
			systemLog.log(SystemLogEvent.of(SystemEventType.REGISTRATION_COMPLETED, SystemLogStatus.SUCCESS)
					.userId(user.getId())
					.recipient(email));
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.LOGIN_SUCCESS, SystemLogStatus.SUCCESS)
				.userId(user.getId())
				.recipient(email));
		return OtpVerifyResponse.of(tokenService.issue(user), newUser);
	}

	private void sendEmailOtp(String email) {
		User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		if (user != null && user.getStatus() != UserStatus.ACTIVE) {
			systemLog.log(SystemLogEvent.of(SystemEventType.OTP_SEND_SKIPPED, SystemLogStatus.SKIPPED)
					.channel(SystemLogChannel.EMAIL)
					.userId(user.getId())
					.recipient(email)
					.error("USER_NOT_ACTIVE", "User status is " + user.getStatus()));
			return;
		}
		OtpPurpose purpose = user == null ? OtpPurpose.SIGN_UP : OtpPurpose.SIGN_IN;
		String otp = generateOtp();
		String key = key(email);

		// Creating a new challenge replaces any previous one, which invalidates the older OTP.
		store.create(
				key,
				Map.of(HASH, otpHasher.hash(otp), PURPOSE, purpose.name(), ATTEMPTS, "0"),
				Duration.ofSeconds(otpProperties.getTtlSeconds()));
		try {
			otpEmailSender.sendOtp(email, otp);
		}
		catch (EmailDeliveryException ex) {
			store.delete(key);
			systemLog.log(SystemLogEvent.of(SystemEventType.OTP_SEND_FAILED, SystemLogStatus.FAILURE)
					.channel(SystemLogChannel.EMAIL)
					.userId(user == null ? null : user.getId())
					.recipient(email)
					.provider(PROVIDER)
					.error("EMAIL_DELIVERY_FAILED", ex.getMessage())
					.meta("purpose", purpose.name()));
			throw ex;
		}
		systemLog.log(SystemLogEvent.of(SystemEventType.OTP_SENT, SystemLogStatus.SUCCESS)
				.channel(SystemLogChannel.EMAIL)
				.userId(user == null ? null : user.getId())
				.recipient(email)
				.provider(PROVIDER)
				.meta("purpose", purpose.name())
				.meta("ttlSeconds", otpProperties.getTtlSeconds()));
	}

	private User register(String email) {
		try {
			return emailUserRegistrar.register(email);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent verification created the account first; sign that account in.
			return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> ex);
		}
	}

	private OtpVerificationFailedException failed(String email, String reason, SystemEventType type) {
		systemLog.log(SystemLogEvent.of(type, SystemLogStatus.FAILURE)
				.channel(SystemLogChannel.EMAIL)
				.recipient(email)
				.error(reason, null));
		return new OtpVerificationFailedException();
	}

	private void touchProvider(User user, String email) {
		UserAuthProvider provider = userAuthProviderRepository
				.findByProviderAndProviderUserId(AuthProvider.EMAIL, email)
				.orElseGet(() -> {
					UserAuthProvider created = new UserAuthProvider();
					created.setUser(user);
					created.setProvider(AuthProvider.EMAIL);
					created.setProviderUserId(email);
					return userAuthProviderRepository.save(created);
				});
		provider.setLastUsedAt(Instant.now());
	}



	private static int attempts(Map<String, String> challenge) {
		try {
			return Integer.parseInt(challenge.getOrDefault(ATTEMPTS, "0"));
		}
		catch (NumberFormatException ex) {
			return Integer.MAX_VALUE;
		}
	}

	private static String key(String email) {
		return KEY_PREFIX + email;
	}

	private String generateOtp() {
		int otp = 100000 + secureRandom.nextInt(900000);
		return String.valueOf(otp);
	}

}
