package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.email.OtpEmailSender;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import com.example.currency.auth.service.ContactIdentifierService.IdentifiedContact;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

	private final ContactIdentifierService contactIdentifierService;
	private final UserRepository userRepository;
	private final OtpEmailSender otpEmailSender;
	private final OtpVerificationRepository otpRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;
	private final EmailUserRegistrar emailUserRegistrar;
	private final OtpHasher otpHasher;
	private final OtpProperties otpProperties;
	private final TokenService tokenService;
	private final SecureRandom secureRandom = new SecureRandom();

	public OtpService(
			ContactIdentifierService contactIdentifierService,
			UserRepository userRepository,
			OtpEmailSender otpEmailSender,
			OtpVerificationRepository otpRepository,
			UserAuthProviderRepository userAuthProviderRepository,
			EmailUserRegistrar emailUserRegistrar,
			OtpHasher otpHasher,
			OtpProperties otpProperties,
			TokenService tokenService) {
		this.contactIdentifierService = contactIdentifierService;
		this.userRepository = userRepository;
		this.otpEmailSender = otpEmailSender;
		this.otpRepository = otpRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
		this.emailUserRegistrar = emailUserRegistrar;
		this.otpHasher = otpHasher;
		this.otpProperties = otpProperties;
		this.tokenService = tokenService;
	}

	@Transactional
	public void sendOtp(String contact) {
		IdentifiedContact identified = contactIdentifierService.identify(contact);
		if (identified.type() == OtpIdentifierType.PHONE) {
			throw new PhoneOtpNotSupportedException();
		}
		sendEmailOtp(identified.value());
	}

	@Transactional(noRollbackFor = OtpVerificationFailedException.class)
	public AuthTokenResponse verifyOtp(String contact, String otp) {
		IdentifiedContact identified = contactIdentifierService.identify(contact);
		if (identified.type() == OtpIdentifierType.PHONE) {
			throw new PhoneOtpNotSupportedException();
		}
		String email = identified.value();
		OtpVerification challenge = otpRepository
				.findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
						email,
						OtpIdentifierType.EMAIL)
				.filter(row -> row.getExpiresAt().isAfter(Instant.now()))
				.filter(row -> row.getAttemptCount() < otpProperties.getMaxAttempts())
				.orElseThrow(OtpVerificationFailedException::new);

		if (!otpHasher.matches(otp, challenge.getOtpHash())) {
			challenge.setAttemptCount(challenge.getAttemptCount() + 1);
			throw new OtpVerificationFailedException();
		}

		User user = resolveUser(challenge, email);
		user.setLastLoginAt(Instant.now());
		user = userRepository.save(user);
		challenge.setUser(user);
		challenge.setVerifiedAt(Instant.now());
		return tokenService.issue(user);
	}

	private void sendEmailOtp(String email) {
		User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		if (user != null && user.getStatus() != UserStatus.ACTIVE) {
			return;
		}
		expirePrevious(email);
		String otp = generateOtp();
		OtpVerification verification = new OtpVerification();
		verification.setUser(user);
		verification.setIdentifier(email);
		verification.setIdentifierType(OtpIdentifierType.EMAIL);
		verification.setPurpose(user == null ? OtpPurpose.SIGN_UP : OtpPurpose.SIGN_IN);
		//verification.setOtpHash(otpHasher.hash(otp));
	    verification.setOtpHash(otp);  //for testing keep the otp unhashed
		verification.setExpiresAt(Instant.now().plusSeconds(otpProperties.getTtlSeconds()));
		verification.setAttemptCount(0);
		otpRepository.save(verification);
		otpEmailSender.sendOtp(email, otp);
	}

	private User resolveUser(OtpVerification challenge, String email) {
		User user = challenge.getUser();
		if (user == null) {
			user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		}
		if (user == null) {
			try {
				user = emailUserRegistrar.register(email);
			}
			catch (DataIntegrityViolationException ex) {
				user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> ex);
			}
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new OtpVerificationFailedException();
		}
		touchProvider(user, email);
		return user;
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

	private void expirePrevious(String email) {
		Instant now = Instant.now();
		for (OtpVerification existing : otpRepository.findByIdentifierAndIdentifierTypeAndVerifiedAtIsNull(
				email,
				OtpIdentifierType.EMAIL)) {
			existing.setExpiresAt(now);
		}
	}

	private String generateOtp() {
		int otp = 100000 + secureRandom.nextInt(900000);
		return String.valueOf(otp);
	}

}
