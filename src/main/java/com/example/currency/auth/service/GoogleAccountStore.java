package com.example.currency.auth.service;

import com.example.currency.auth.oidc.VerifiedGoogleIdentity;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The two Google writes that can lose a race against the database's unique constraints. Each runs in its own
 * transaction and flushes, so a {@code DataIntegrityViolationException} surfaces here and the caller's
 * transaction is not marked rollback-only; the caller then re-reads and decides.
 */
@Component
public class GoogleAccountStore {

	private static final int MAX_DISPLAY_NAME = 100;
	private static final int MAX_PICTURE_URL = 2048;

	private final UserRepository userRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;

	public GoogleAccountStore(UserRepository userRepository, UserAuthProviderRepository userAuthProviderRepository) {
		this.userRepository = userRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
	}

	/** Creates the account for a Google-verified email and links its Google identity. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public User register(VerifiedGoogleIdentity identity, String email) {
		Instant now = Instant.now();
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(now);
		user.setLastLoginAt(now);
		applyProfile(user, identity.name(), identity.pictureUrl());
		user = userRepository.saveAndFlush(user);
		insertGoogleLink(user, identity.subject(), now);
		return user;
	}

	/** Links a Google identity to an existing user; the unique constraints reject duplicates. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void link(UUID userId, String googleSubject) {
		User user = userRepository.getReferenceById(userId);
		insertGoogleLink(user, googleSubject, Instant.now());
	}

	/**
	 * Copies the display name and picture Google supplied onto the user. A claim Google did not send (or sent
	 * blank, over-long or not an http(s) URL) leaves the stored value untouched.
	 */
	public static void applyProfile(User user, String name, String pictureUrl) {
		String displayName = name == null || name.isBlank() ? null : truncate(name.trim(), MAX_DISPLAY_NAME);
		if (displayName != null) {
			user.setDisplayName(displayName);
		}
		String picture = httpUrlOrNull(pictureUrl);
		if (picture != null) {
			user.setProfileImageUrl(picture);
		}
	}

	private void insertGoogleLink(User user, String googleSubject, Instant now) {
		UserAuthProvider provider = new UserAuthProvider();
		provider.setUser(user);
		provider.setProvider(AuthProvider.GOOGLE);
		provider.setProviderUserId(googleSubject);
		provider.setLastUsedAt(now);
		userAuthProviderRepository.saveAndFlush(provider);
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String httpUrlOrNull(String value) {
		if (value == null || value.length() > MAX_PICTURE_URL) {
			return null;
		}
		try {
			java.net.URI uri = new java.net.URI(value);
			String scheme = uri.getScheme();
			return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null
					? value : null;
		}
		catch (java.net.URISyntaxException ex) {
			return null;
		}
	}

}
