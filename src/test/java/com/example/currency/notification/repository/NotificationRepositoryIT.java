package com.example.currency.notification.repository;

import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.enums.DeliveryStatus;
import com.example.currency.notification.enums.NotificationType;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class NotificationRepositoryIT {

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
	private NotificationRepository notificationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void listsUserNotificationsNewestFirst() {
		User user = userRepository.saveAndFlush(activeUser("inbox@example.com"));
		Notification older = notificationRepository.saveAndFlush(notification(user, "Older"));
		Notification newer = notificationRepository.saveAndFlush(notification(user, "Newer"));
		updateCreatedAt(older.getId(), Instant.parse("2026-10-10T00:00:00Z"));
		updateCreatedAt(newer.getId(), Instant.parse("2026-10-12T00:00:00Z"));
		entityManager.clear();

		Page<Notification> page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(
				user.getId(),
				PageRequest.of(0, 10));

		assertEquals(List.of("Newer", "Older"),
				page.getContent().stream().map(Notification::getTitle).toList());
	}

	@Test
	void unreadFilterReturnsOnlyUnread() {
		User user = userRepository.saveAndFlush(activeUser("unread@example.com"));
		Notification unread = notificationRepository.saveAndFlush(notification(user, "Unread"));
		Notification read = notificationRepository.saveAndFlush(notification(user, "Read"));
		read.setRead(true);
		read.setReadAt(Instant.parse("2026-10-11T00:00:00Z"));
		notificationRepository.saveAndFlush(read);
		entityManager.clear();

		Page<Notification> page = notificationRepository.findByUser_IdAndReadFalseOrderByCreatedAtDesc(
				user.getId(),
				PageRequest.of(0, 10));

		assertEquals(1, page.getTotalElements());
		assertEquals(unread.getId(), page.getContent().getFirst().getId());
	}

	@Test
	void markAllUnreadAsReadIsScopedToUser() {
		User owner = userRepository.saveAndFlush(activeUser("owner-notif@example.com"));
		User other = userRepository.saveAndFlush(activeUser("other-notif@example.com"));
		Notification ownerUnread = notificationRepository.saveAndFlush(notification(owner, "Owner unread"));
		Notification otherUnread = notificationRepository.saveAndFlush(notification(other, "Other unread"));
		entityManager.clear();

		int updated = notificationRepository.markAllUnreadAsRead(owner.getId(), Instant.parse("2026-10-12T12:00:00Z"));

		assertEquals(1, updated);
		entityManager.clear();
		assertTrue(notificationRepository.findById(ownerUnread.getId()).orElseThrow().isRead());
		assertFalse(notificationRepository.findById(otherUnread.getId()).orElseThrow().isRead());
	}

	@Test
	void deleteIsScopedToUser() {
		User owner = userRepository.saveAndFlush(activeUser("del-owner@example.com"));
		User other = userRepository.saveAndFlush(activeUser("del-other@example.com"));
		Notification ownerNotification = notificationRepository.saveAndFlush(notification(owner, "Mine"));
		Notification otherNotification = notificationRepository.saveAndFlush(notification(other, "Theirs"));
		entityManager.clear();

		int deleted = notificationRepository.deleteByIdAndUser_Id(ownerNotification.getId(), owner.getId());

		assertEquals(1, deleted);
		assertTrue(notificationRepository.findById(ownerNotification.getId()).isEmpty());
		assertTrue(notificationRepository.findById(otherNotification.getId()).isPresent());
	}

	private void updateCreatedAt(UUID notificationId, Instant createdAt) {
		entityManager.createNativeQuery("UPDATE notifications SET created_at = :createdAt WHERE id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", notificationId)
				.executeUpdate();
	}

	private static User activeUser(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static Notification notification(User user, String title) {
		Notification notification = new Notification();
		notification.setUser(user);
		notification.setType(NotificationType.PRICE_ALERT);
		notification.setTitle(title);
		notification.setBody("Body for " + title);
		notification.setStatus(DeliveryStatus.PENDING);
		return notification;
	}

}
