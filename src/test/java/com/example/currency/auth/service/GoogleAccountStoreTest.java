package com.example.currency.auth.service;

import com.example.currency.auth.oidc.VerifiedGoogleIdentity;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleAccountStoreTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserAuthProviderRepository providerRepository;

	private GoogleAccountStore store;

	@BeforeEach
	void setUp() {
		store = new GoogleAccountStore(userRepository, providerRepository);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(providerRepository.saveAndFlush(any(UserAuthProvider.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registersAVerifiedActiveAccountAndLinksTheGoogleSubject() {
		User user = store.register(new VerifiedGoogleIdentity(
				"sub-9", "p@example.com", true, "Pat Person", "https://lh3.googleusercontent.com/a/p"), "p@example.com");

		assertEquals("p@example.com", user.getEmail());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertNotNull(user.getEmailVerifiedAt());
		assertEquals("Pat Person", user.getDisplayName());
		assertEquals("https://lh3.googleusercontent.com/a/p", user.getProfileImageUrl());
		ArgumentCaptor<UserAuthProvider> provider = ArgumentCaptor.forClass(UserAuthProvider.class);
		verify(providerRepository).saveAndFlush(provider.capture());
		assertEquals(AuthProvider.GOOGLE, provider.getValue().getProvider());
		assertEquals("sub-9", provider.getValue().getProviderUserId());
		assertEquals(user, provider.getValue().getUser());
	}

	@Test
	void dropsUnsafeProfileFieldsInsteadOfStoringThem() {
		User javascript = store.register(new VerifiedGoogleIdentity(
				"s1", "a@example.com", true, "x".repeat(300), "javascript:alert(1)"), "a@example.com");
		User oversized = store.register(new VerifiedGoogleIdentity(
				"s2", "b@example.com", true, null, "https://example.com/" + "p".repeat(3000)), "b@example.com");
		User garbage = store.register(new VerifiedGoogleIdentity(
				"s3", "c@example.com", true, null, "not a url"), "c@example.com");

		assertEquals(100, javascript.getDisplayName().length());
		assertNull(javascript.getProfileImageUrl());
		assertNull(oversized.getProfileImageUrl());
		assertNull(oversized.getDisplayName());
		assertNull(garbage.getProfileImageUrl());
	}

	@Test
	void linkingAddsOnlyAGoogleProviderRowForThatUser() {
		UUID userId = UUID.randomUUID();
		User reference = new User();
		when(userRepository.getReferenceById(userId)).thenReturn(reference);

		store.link(userId, "sub-9");

		ArgumentCaptor<UserAuthProvider> provider = ArgumentCaptor.forClass(UserAuthProvider.class);
		verify(providerRepository).saveAndFlush(provider.capture());
		assertEquals(AuthProvider.GOOGLE, provider.getValue().getProvider());
		assertEquals("sub-9", provider.getValue().getProviderUserId());
		assertTrue(provider.getValue().getUser() == reference);
	}


	@Test
	void applyProfileIgnoresBlankNamesAndUnsafePictureUrls() {
		User user = new User();
		user.setDisplayName("Kept");
		user.setProfileImageUrl("https://example.com/kept.png");

		GoogleAccountStore.applyProfile(user, "   ", "javascript:alert(1)");
		assertEquals("Kept", user.getDisplayName());
		assertEquals("https://example.com/kept.png", user.getProfileImageUrl());

		GoogleAccountStore.applyProfile(user, "  New Name  ", "https://lh3.googleusercontent.com/a/n");
		assertEquals("New Name", user.getDisplayName());
		assertEquals("https://lh3.googleusercontent.com/a/n", user.getProfileImageUrl());

		GoogleAccountStore.applyProfile(user, "x".repeat(150), null);
		assertEquals(100, user.getDisplayName().length());
		assertEquals("https://lh3.googleusercontent.com/a/n", user.getProfileImageUrl());
	}
}
