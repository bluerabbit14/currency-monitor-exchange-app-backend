package com.example.currency.notification.service;

import com.example.currency.notification.dto.NotificationPreferenceResponse;
import com.example.currency.notification.dto.UpdateNotificationPreferenceRequest;
import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.notification.exception.EmptyNotificationPreferenceUpdateException;
import com.example.currency.notification.repository.NotificationPreferenceRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

	@Mock
	private NotificationPreferenceRepository preferenceRepository;

	@Mock
	private UserRepository userRepository;

	private NotificationPreferenceService preferenceService;

	@BeforeEach
	void setUp() {
		preferenceService = new NotificationPreferenceService(preferenceRepository, userRepository);
	}

	@Test
	void getOrCreateReturnsExistingPreferences() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

		NotificationPreferenceResponse response = preferenceService.getOrCreate(userId);

		assertEquals(true, response.pushEnabled());
		assertEquals(true, response.inAppEnabled());
		assertEquals(false, response.emailEnabled());
		verify(preferenceRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).getReferenceById(any());
	}

	@Test
	void getOrCreateInitializesDefaultsWhenMissing() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		NotificationPreference created = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(preferenceRepository.saveAndFlush(any(NotificationPreference.class))).thenReturn(created);

		NotificationPreferenceResponse response = preferenceService.getOrCreate(userId);

		assertEquals(true, response.pushEnabled());
		assertEquals(true, response.inAppEnabled());
		assertEquals(false, response.emailEnabled());

		ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
		verify(preferenceRepository).saveAndFlush(captor.capture());
		assertTrue(captor.getValue().isPushEnabled());
		assertTrue(captor.getValue().isInAppEnabled());
		assertFalse(captor.getValue().isEmailEnabled());
	}

	@Test
	void repeatedGetOrCreateDoesNotCreateWhenRecordExists() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

		preferenceService.getOrCreate(userId);
		preferenceService.getOrCreate(userId);

		verify(preferenceRepository, times(2)).findByUserId(userId);
		verify(preferenceRepository, never()).saveAndFlush(any());
	}

	@Test
	void concurrentCreateFallsBackToExistingRecord() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(preferenceRepository.saveAndFlush(any(NotificationPreference.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		NotificationPreferenceResponse response = preferenceService.getOrCreate(userId);

		assertEquals(false, response.emailEnabled());
		verify(preferenceRepository, times(2)).findByUserId(userId);
	}

	@Test
	void patchUpdatesOnlyPushEnabled() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(existing)).thenReturn(existing);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(false, null, null));

		assertFalse(response.pushEnabled());
		assertTrue(response.inAppEnabled());
		assertFalse(response.emailEnabled());
	}

	@Test
	void patchUpdatesOnlyInAppEnabled() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(existing)).thenReturn(existing);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(null, false, null));

		assertTrue(response.pushEnabled());
		assertFalse(response.inAppEnabled());
		assertFalse(response.emailEnabled());
	}

	@Test
	void patchUpdatesOnlyEmailEnabled() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(existing)).thenReturn(existing);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(null, null, true));

		assertTrue(response.pushEnabled());
		assertTrue(response.inAppEnabled());
		assertTrue(response.emailEnabled());
	}

	@Test
	void patchUpdatesMultipleSettings() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(existing)).thenReturn(existing);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(false, true, false));

		assertFalse(response.pushEnabled());
		assertTrue(response.inAppEnabled());
		assertFalse(response.emailEnabled());
	}

	@Test
	void explicitFalseDisablesSetting() {
		UUID userId = UUID.randomUUID();
		NotificationPreference existing = preference(userId, true, true, true,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(existing)).thenReturn(existing);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(false, false, false));

		assertFalse(response.pushEnabled());
		assertFalse(response.inAppEnabled());
		assertFalse(response.emailEnabled());
	}

	@Test
	void emptyPatchIsRejected() {
		UUID userId = UUID.randomUUID();

		assertThrows(
				EmptyNotificationPreferenceUpdateException.class,
				() -> preferenceService.update(userId, new UpdateNotificationPreferenceRequest(null, null, null)));

		verify(preferenceRepository, never()).findByUserId(any());
		verify(preferenceRepository, never()).save(any());
	}

	@Test
	void updateCreatesDefaultsWhenMissingThenAppliesPatch() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		NotificationPreference created = preference(userId, true, true, false,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(preferenceRepository.saveAndFlush(any(NotificationPreference.class))).thenReturn(created);
		when(preferenceRepository.save(created)).thenReturn(created);

		NotificationPreferenceResponse response = preferenceService.update(
				userId,
				new UpdateNotificationPreferenceRequest(null, null, true));

		assertTrue(response.emailEnabled());
		assertTrue(response.pushEnabled());
		assertTrue(response.inAppEnabled());
	}

	private static User userReference(UUID userId) {
		User user = new User();
		setField(user, "id", userId);
		return user;
	}

	private static NotificationPreference preference(
			UUID userId,
			boolean pushEnabled,
			boolean inAppEnabled,
			boolean emailEnabled,
			Instant createdAt) {
		NotificationPreference preference = new NotificationPreference();
		preference.setUser(userReference(userId));
		preference.setPushEnabled(pushEnabled);
		preference.setInAppEnabled(inAppEnabled);
		preference.setEmailEnabled(emailEnabled);
		setField(preference, "userId", userId);
		setField(preference, "createdAt", createdAt);
		setField(preference, "updatedAt", createdAt);
		return preference;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field field = findField(target.getClass(), name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

}
