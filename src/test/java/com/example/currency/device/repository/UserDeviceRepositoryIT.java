package com.example.currency.device.repository;

import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserDevice;
import com.example.currency.user.enums.DevicePlatform;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserDeviceRepositoryIT {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	@Autowired
	private UserDeviceRepository userDeviceRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void rejectsDuplicateDeviceTokens() {
		User user = userRepository.saveAndFlush(activeUser("dup-device@example.com"));
		userDeviceRepository.saveAndFlush(device(user, "shared-token", "Phone A", true));

		assertThrows(DataIntegrityViolationException.class,
				() -> userDeviceRepository.saveAndFlush(device(user, "shared-token", "Phone B", true)));
	}

	@Test
	void listsDevicesForUserNewestLastSeenFirstIncludingInactive() {
		User owner = userRepository.saveAndFlush(activeUser("list-owner@example.com"));
		User other = userRepository.saveAndFlush(activeUser("list-other@example.com"));

		UserDevice older = userDeviceRepository.saveAndFlush(device(owner, "token-older", "Older", true));
		UserDevice newer = userDeviceRepository.saveAndFlush(device(owner, "token-newer", "Newer", false));
		userDeviceRepository.saveAndFlush(device(other, "token-other", "Other", true));

		updateLastSeenAt(older.getId(), Instant.parse("2026-10-10T00:00:00Z"));
		updateLastSeenAt(newer.getId(), Instant.parse("2026-10-12T00:00:00Z"));
		entityManager.clear();

		List<UserDevice> devices = userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(owner.getId());

		assertEquals(List.of(newer.getId(), older.getId()),
				devices.stream().map(UserDevice::getId).toList());
		assertFalse(devices.get(0).isActive());
		assertTrue(devices.get(1).isActive());
	}

	@Test
	void softDeactivationRetainsRowAndToken() {
		User user = userRepository.saveAndFlush(activeUser("soft-delete@example.com"));
		UserDevice device = userDeviceRepository.saveAndFlush(device(user, "retain-token", "Phone", true));

		device.setActive(false);
		userDeviceRepository.saveAndFlush(device);
		entityManager.clear();

		UserDevice reloaded = userDeviceRepository.findById(device.getId()).orElseThrow();
		assertFalse(reloaded.isActive());
		assertEquals("retain-token", reloaded.getDeviceToken());
		assertTrue(userDeviceRepository.findByDeviceToken("retain-token").isPresent());
	}

	@Test
	void reassignmentKeepsSingleRowPerToken() {
		User first = userRepository.saveAndFlush(activeUser("first-owner@example.com"));
		User second = userRepository.saveAndFlush(activeUser("second-owner@example.com"));
		UserDevice device = userDeviceRepository.saveAndFlush(device(first, "reassign-token", "Phone", false));
		Instant createdAt = device.getCreatedAt();

		device.setUser(second);
		device.setActive(true);
		device.setDeviceName("Second Owner Phone");
		device.setLastSeenAt(Instant.parse("2026-10-12T06:00:00Z"));
		userDeviceRepository.saveAndFlush(device);
		entityManager.clear();

		UserDevice reloaded = userDeviceRepository.findByDeviceToken("reassign-token").orElseThrow();
		assertEquals(second.getId(), reloaded.getUser().getId());
		assertTrue(reloaded.isActive());
		assertEquals(createdAt, reloaded.getCreatedAt());
		assertEquals(1, userDeviceRepository.findAll().stream()
				.filter(row -> "reassign-token".equals(row.getDeviceToken()))
				.count());
	}

	@Test
	void ownershipLookupScopesByUser() {
		User owner = userRepository.saveAndFlush(activeUser("owned@example.com"));
		User other = userRepository.saveAndFlush(activeUser("not-owned@example.com"));
		UserDevice device = userDeviceRepository.saveAndFlush(device(owner, "owned-token", "Phone", true));

		assertTrue(userDeviceRepository.findByIdAndUserId(device.getId(), owner.getId()).isPresent());
		assertTrue(userDeviceRepository.findByIdAndUserId(device.getId(), other.getId()).isEmpty());
	}

	private void updateLastSeenAt(UUID deviceId, Instant lastSeenAt) {
		entityManager.createNativeQuery("UPDATE user_devices SET last_seen_at = :lastSeenAt WHERE id = :id")
				.setParameter("lastSeenAt", lastSeenAt)
				.setParameter("id", deviceId)
				.executeUpdate();
	}

	private static User activeUser(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static UserDevice device(User user, String token, String name, boolean active) {
		UserDevice device = new UserDevice();
		device.setUser(user);
		device.setDeviceToken(token);
		device.setPlatform(DevicePlatform.ANDROID);
		device.setDeviceName(name);
		device.setActive(active);
		device.setLastSeenAt(Instant.parse("2026-10-12T04:00:00Z"));
		return device;
	}

}
