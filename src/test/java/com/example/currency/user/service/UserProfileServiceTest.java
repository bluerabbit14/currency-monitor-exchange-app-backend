package com.example.currency.user.service;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.user.dto.UpdateUserProfileRequest;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

	@Mock
	private UserRepository userRepository;

	private UserProfileService userProfileService;

	@BeforeEach
	void setUp() {
		userProfileService = new UserProfileService(userRepository);
		lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void getProfileReturnsTheActiveUser() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.getProfile(user.getId());

		assertEquals(new UserProfileResponse(
				user.getId(),
				"ada@example.com",
				"+15551212",
				"Ada",
				"https://cdn.example.com/ada.png",
				user.getCreatedAt(),
				user.getUpdatedAt()), response);
		verify(userRepository, never()).save(any());
	}

	@Test
	void patchUpdatesOnlyProvidedFields() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(
				user.getId(),
				new UpdateUserProfileRequest("  Grace Hopper  ", null));

		assertEquals("Grace Hopper", response.displayName());
		assertEquals("https://cdn.example.com/ada.png", response.profileImageUrl());
		assertEquals("ada@example.com", response.email());
		assertEquals("+15551212", response.phoneNumber());
	}

	@Test
	void patchClearsBlankFields() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(
				user.getId(),
				new UpdateUserProfileRequest("   ", " \t "));

		assertNull(response.displayName());
		assertNull(response.profileImageUrl());
	}

	@Test
	void rejectsInactiveUser() {
		User user = activeUser();
		user.setStatus(UserStatus.SUSPENDED);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertThrows(InvalidAccessTokenException.class, () -> userProfileService.getProfile(user.getId()));
		verify(userRepository, never()).save(any());
	}

	private static User activeUser() {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
		Instant updatedAt = Instant.parse("2026-02-01T00:00:00Z");
		setField(user, "createdAt", createdAt);
		setField(user, "updatedAt", updatedAt);
		user.setEmail("ada@example.com");
		user.setPhoneNumber("+15551212");
		user.setDisplayName("Ada");
		user.setProfileImageUrl("https://cdn.example.com/ada.png");
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> type = target.getClass();
		while (type != null) {
			try {
				var field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(target, value);
				return;
			}
			catch (NoSuchFieldException ex) {
				type = type.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new IllegalStateException(name);
	}

}
