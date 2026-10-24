package com.example.currency.device.service;

import com.example.currency.device.dto.DeviceResponse;
import com.example.currency.device.dto.RegisterDeviceRequest;
import com.example.currency.device.dto.UpdateDeviceRequest;
import com.example.currency.device.exception.DeviceNotFoundException;
import com.example.currency.device.repository.UserDeviceRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserDevice;
import com.example.currency.user.enums.DevicePlatform;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

	private static final String TOKEN = "example-fcm-registration-token";

	@Mock
	private UserDeviceRepository userDeviceRepository;

	@Mock
	private UserRepository userRepository;

	private DeviceService deviceService;

	@BeforeEach
	void setUp() {
		deviceService = new DeviceService(userDeviceRepository, userRepository);
	}

	@Test
	void registersNewAndroidDevice() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		UserDevice saved = device(user, TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));

		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(userDeviceRepository.saveAndFlush(any(UserDevice.class))).thenReturn(saved);

		DeviceResponse response = deviceService.register(userId, new RegisterDeviceRequest(
				TOKEN, DevicePlatform.ANDROID, "My Android Phone"));

		assertEquals(saved.getId(), response.id());
		assertEquals(DevicePlatform.ANDROID, response.platform());
		assertEquals("My Android Phone", response.deviceName());
		assertTrue(response.isActive());
		assertNull(responseGetTokenField(response));

		ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
		verify(userDeviceRepository).saveAndFlush(captor.capture());
		assertEquals(TOKEN, captor.getValue().getDeviceToken());
		assertTrue(captor.getValue().isActive());
	}

	@Test
	void refreshesExistingRegistrationForSameUser() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		Instant createdAt = Instant.parse("2026-10-10T00:00:00Z");
		UserDevice existing = device(user, TOKEN, true, createdAt);
		existing.setLastSeenAt(Instant.parse("2026-10-10T01:00:00Z"));

		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(userDeviceRepository.save(existing)).thenReturn(existing);

		DeviceResponse response = deviceService.register(userId, new RegisterDeviceRequest(
				TOKEN, DevicePlatform.ANDROID, "Updated Phone"));

		assertEquals(createdAt, response.createdAt());
		assertEquals("Updated Phone", response.deviceName());
		assertTrue(response.isActive());
		assertTrue(existing.getLastSeenAt().isAfter(Instant.parse("2026-10-10T01:00:00Z")));
		verify(userDeviceRepository, never()).saveAndFlush(any());
	}

	@Test
	void reactivatesInactiveDeviceOnReregistration() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		UserDevice existing = device(user, TOKEN, false, Instant.parse("2026-10-09T00:00:00Z"));

		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(userDeviceRepository.save(existing)).thenReturn(existing);

		DeviceResponse response = deviceService.register(userId, new RegisterDeviceRequest(
				TOKEN, DevicePlatform.ANDROID, null));

		assertTrue(response.isActive());
		assertTrue(existing.isActive());
	}

	@Test
	void reassignsTokenFromAnotherUser() {
		UUID previousOwnerId = UUID.randomUUID();
		UUID newOwnerId = UUID.randomUUID();
		User previousOwner = userReference(previousOwnerId);
		User newOwner = userReference(newOwnerId);
		Instant createdAt = Instant.parse("2026-10-08T00:00:00Z");
		UserDevice existing = device(previousOwner, TOKEN, true, createdAt);

		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(newOwnerId)).thenReturn(newOwner);
		when(userDeviceRepository.save(existing)).thenReturn(existing);

		DeviceResponse response = deviceService.register(newOwnerId, new RegisterDeviceRequest(
				TOKEN, DevicePlatform.ANDROID, "New Owner Phone"));

		assertEquals(newOwner, existing.getUser());
		assertEquals(createdAt, response.createdAt());
		assertEquals("New Owner Phone", response.deviceName());
		assertTrue(response.isActive());
	}

	@Test
	void concurrentRegistrationReusesExistingRow() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		UserDevice existing = device(user, TOKEN, true, Instant.parse("2026-10-07T00:00:00Z"));

		when(userDeviceRepository.findByDeviceToken(TOKEN))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(userDeviceRepository.saveAndFlush(any(UserDevice.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate token"));
		when(userDeviceRepository.save(existing)).thenReturn(existing);

		DeviceResponse response = deviceService.register(userId, new RegisterDeviceRequest(
				TOKEN, DevicePlatform.ANDROID, "Race Phone"));

		assertEquals(existing.getId(), response.id());
		assertEquals("Race Phone", response.deviceName());
	}

	@Test
	void rejectsUnsupportedPlatform() {
		UUID userId = UUID.randomUUID();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> deviceService.register(userId, new RegisterDeviceRequest(
						TOKEN, DevicePlatform.IOS, "iPhone")));

		assertEquals("platform must be ANDROID", ex.getMessage());
		verify(userDeviceRepository, never()).saveAndFlush(any());
		verify(userDeviceRepository, never()).save(any());
	}

	@Test
	void listsOnlyRequestedUsersDevices() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		UserDevice first = device(user, "token-1", true, Instant.parse("2026-10-12T04:00:00Z"));
		UserDevice second = device(user, "token-2", false, Instant.parse("2026-10-11T04:00:00Z"));

		when(userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
				.thenReturn(List.of(first, second));

		List<DeviceResponse> responses = deviceService.list(userId);

		assertEquals(List.of(first.getId(), second.getId()),
				responses.stream().map(DeviceResponse::id).toList());
		assertTrue(responses.get(0).isActive());
		assertFalse(responses.get(1).isActive());
		verify(userDeviceRepository).findByUserIdOrderByLastSeenAtDesc(userId);
	}

	@Test
	void updatesDeviceNameAndClearsBlankName() {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UserDevice device = device(userReference(userId), TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));
		setField(device, "id", deviceId);
		device.setDeviceName("Old Name");

		when(userDeviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.of(device));
		when(userDeviceRepository.save(device)).thenReturn(device);

		DeviceResponse updated = deviceService.update(userId, deviceId, new UpdateDeviceRequest("New Name"));
		assertEquals("New Name", updated.deviceName());

		DeviceResponse cleared = deviceService.update(userId, deviceId, new UpdateDeviceRequest("  "));
		assertNull(cleared.deviceName());
	}

	@Test
	void updateIgnoresOmittedDeviceName() {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UserDevice device = device(userReference(userId), TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));
		setField(device, "id", deviceId);
		device.setDeviceName("Keep Me");

		when(userDeviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.of(device));

		DeviceResponse response = deviceService.update(userId, deviceId, new UpdateDeviceRequest(null));

		assertEquals("Keep Me", response.deviceName());
		verify(userDeviceRepository, never()).save(any());
	}

	@Test
	void softDeactivatesOwnedDeviceIdempotently() {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		UserDevice device = device(userReference(userId), TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));
		setField(device, "id", deviceId);

		when(userDeviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.of(device));
		when(userDeviceRepository.save(device)).thenReturn(device);

		deviceService.unregister(userId, deviceId);
		assertFalse(device.isActive());
		verify(userDeviceRepository).save(device);

		deviceService.unregister(userId, deviceId);
		verify(userDeviceRepository).save(device);
	}

	@Test
	void rejectsUpdateOrUnregisterForOtherUsersDevice() {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(userDeviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

		assertThrows(DeviceNotFoundException.class,
				() -> deviceService.update(userId, deviceId, new UpdateDeviceRequest("Nope")));
		assertThrows(DeviceNotFoundException.class,
				() -> deviceService.unregister(userId, deviceId));
		verify(userDeviceRepository, never()).save(any());
	}

	@Test
	void logoutDeactivatesOnlyTheCallersOwnDevice() {
		UUID userId = UUID.randomUUID();
		UserDevice mine = device(userReference(userId), TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));
		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.of(mine));
		when(userDeviceRepository.save(mine)).thenReturn(mine);

		deviceService.deactivateByToken(userId, " " + TOKEN + " ");

		assertFalse(mine.isActive());
		verify(userDeviceRepository).save(mine);
	}

	@Test
	void logoutLeavesADeviceAlreadyHandedToAnotherUser() {
		UUID previousOwner = UUID.randomUUID();
		UserDevice handedOver = device(userReference(UUID.randomUUID()), TOKEN, true, Instant.parse("2026-10-12T04:00:00Z"));
		when(userDeviceRepository.findByDeviceToken(TOKEN)).thenReturn(Optional.of(handedOver));

		deviceService.deactivateByToken(previousOwner, TOKEN);

		assertTrue(handedOver.isActive());
		verify(userDeviceRepository, never()).save(any());
	}

	@Test
	void logoutWithoutOrWithUnknownTokenDoesNothing() {
		UUID userId = UUID.randomUUID();
		when(userDeviceRepository.findByDeviceToken("unknown")).thenReturn(Optional.empty());

		deviceService.deactivateByToken(userId, null);
		deviceService.deactivateByToken(userId, "  ");
		deviceService.deactivateByToken(userId, "unknown");

		verify(userDeviceRepository, never()).save(any());
	}

	@Test
	void activeTokensComeOnlyFromTheRecipientsActiveDevices() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		when(userDeviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(
				device(user, "phone", true, Instant.parse("2026-10-12T04:00:00Z")),
				device(user, "tablet", true, Instant.parse("2026-10-12T04:00:00Z"))));

		assertEquals(List.of("phone", "tablet"), deviceService.activeTokens(userId));
	}

	private static String responseGetTokenField(DeviceResponse response) {
		try {
			response.getClass().getDeclaredField("fcmToken");
			return "present";
		}
		catch (NoSuchFieldException ignored) {
			try {
				response.getClass().getDeclaredField("deviceToken");
				return "present";
			}
			catch (NoSuchFieldException ex) {
				return null;
			}
		}
	}

	private static User userReference(UUID userId) {
		User user = new User();
		setField(user, "id", userId);
		return user;
	}

	private static UserDevice device(User user, String token, boolean active, Instant createdAt) {
		UserDevice device = new UserDevice();
		setField(device, "id", UUID.randomUUID());
		setField(device, "createdAt", createdAt);
		setField(device, "updatedAt", createdAt);
		device.setUser(user);
		device.setDeviceToken(token);
		device.setPlatform(DevicePlatform.ANDROID);
		device.setDeviceName("My Android Phone");
		device.setActive(active);
		device.setLastSeenAt(createdAt);
		return device;
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
