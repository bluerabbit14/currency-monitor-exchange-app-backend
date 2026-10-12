package com.example.currency.notification.repository;

import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.user.entity.User;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class NotificationPreferenceRepositoryIT {

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
	private NotificationPreferenceRepository preferenceRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesDefaultsMatchingSchema() {
		User user = userRepository.saveAndFlush(activeUser("prefs@example.com"));

		preferenceRepository.saveAndFlush(preference(user));
		entityManager.clear();

		NotificationPreference loaded = preferenceRepository.findByUserId(user.getId()).orElseThrow();
		assertEquals(user.getId(), loaded.getUserId());
		assertTrue(loaded.isPushEnabled());
		assertTrue(loaded.isInAppEnabled());
		assertFalse(loaded.isEmailEnabled());
		assertTrue(loaded.getCreatedAt() != null);
		assertTrue(loaded.getUpdatedAt() != null);
	}

	@Test
	void rejectsDuplicatePreferencesForSameUser() {
		User user = userRepository.saveAndFlush(activeUser("dup-prefs@example.com"));
		preferenceRepository.saveAndFlush(preference(user));

		assertThrows(DataIntegrityViolationException.class,
				() -> preferenceRepository.saveAndFlush(preference(user)));
	}

	@Test
	void repeatedFindDoesNotCreateDuplicates() {
		User user = userRepository.saveAndFlush(activeUser("repeat@example.com"));
		preferenceRepository.saveAndFlush(preference(user));

		assertTrue(preferenceRepository.findByUserId(user.getId()).isPresent());
		assertTrue(preferenceRepository.findByUserId(user.getId()).isPresent());
		assertEquals(1, preferenceRepository.count());
	}

	@Test
	void deletingUserCascadesPreferences() {
		User user = userRepository.saveAndFlush(activeUser("cascade-prefs@example.com"));
		preferenceRepository.saveAndFlush(preference(user));
		UUID userId = user.getId();

		userRepository.delete(user);
		userRepository.flush();
		entityManager.clear();

		assertFalse(preferenceRepository.findByUserId(userId).isPresent());
	}

	@Test
	void partialUpdatePreservesOmittedFields() {
		User user = userRepository.saveAndFlush(activeUser("patch@example.com"));
		NotificationPreference prefs = preferenceRepository.saveAndFlush(preference(user));

		prefs.setEmailEnabled(true);
		preferenceRepository.saveAndFlush(prefs);
		entityManager.clear();

		NotificationPreference loaded = preferenceRepository.findByUserId(user.getId()).orElseThrow();
		assertTrue(loaded.isPushEnabled());
		assertTrue(loaded.isInAppEnabled());
		assertTrue(loaded.isEmailEnabled());
	}

	private static User activeUser(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static NotificationPreference preference(User user) {
		NotificationPreference preference = new NotificationPreference();
		preference.setUser(user);
		return preference;
	}

}
