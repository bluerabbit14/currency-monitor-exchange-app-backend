package com.example.currency.auth.repository;

import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real migrations on PostgreSQL. Skipped when Docker is unavailable.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserAuthProviderGoogleIT {

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
	private UserRepository userRepository;

	@Autowired
	private UserAuthProviderRepository providerRepository;

	@Test
	void aGoogleSubjectCanBelongToOnlyOneUser() {
		User first = userRepository.saveAndFlush(user("google-first@example.com"));
		User second = userRepository.saveAndFlush(user("google-second@example.com"));
		providerRepository.saveAndFlush(google(first, "sub-shared"));

		assertThrows(DataIntegrityViolationException.class,
				() -> providerRepository.saveAndFlush(google(second, "sub-shared")));
	}

	@Test
	void aUserCanHaveOnlyOneGoogleIdentity() {
		User user = userRepository.saveAndFlush(user("google-one@example.com"));
		providerRepository.saveAndFlush(google(user, "sub-a"));

		assertThrows(DataIntegrityViolationException.class,
				() -> providerRepository.saveAndFlush(google(user, "sub-b")));
	}

	@Test
	void theLimitDoesNotAffectTheEmailProviderAndLookupsByProviderAndSubjectWork() {
		User user = userRepository.saveAndFlush(user("google-mixed@example.com"));
		UserAuthProvider email = new UserAuthProvider();
		email.setUser(user);
		email.setProvider(AuthProvider.EMAIL);
		email.setProviderUserId("google-mixed@example.com");
		providerRepository.saveAndFlush(email);
		providerRepository.saveAndFlush(google(user, "sub-mixed"));

		assertTrue(providerRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE));
		assertEquals(user.getId(), providerRepository
				.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "sub-mixed").orElseThrow().getUser().getId());
	}

	private static User user(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static UserAuthProvider google(User user, String subject) {
		UserAuthProvider provider = new UserAuthProvider();
		provider.setUser(user);
		provider.setProvider(AuthProvider.GOOGLE);
		provider.setProviderUserId(subject);
		return provider;
	}

}
