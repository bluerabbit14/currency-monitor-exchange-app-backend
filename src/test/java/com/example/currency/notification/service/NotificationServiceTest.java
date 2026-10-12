package com.example.currency.notification.service;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.alert.repository.PriceAlertRepository;
import com.example.currency.notification.dto.MarkAllReadResponse;
import com.example.currency.notification.dto.NotificationResponse;
import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.enums.DeliveryStatus;
import com.example.currency.notification.enums.NotificationType;
import com.example.currency.notification.exception.NotificationNotFoundException;
import com.example.currency.notification.repository.NotificationRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PriceAlertRepository priceAlertRepository;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(
				notificationRepository,
				userRepository,
				priceAlertRepository);
	}

	@Test
	void createNotificationInitializesUnreadPendingState() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
			Notification saved = invocation.getArgument(0);
			setField(saved, "id", UUID.randomUUID());
			setField(saved, "createdAt", Instant.parse("2026-10-12T04:00:00Z"));
			return saved;
		});

		Notification created = notificationService.createNotification(
				userId,
				NotificationType.PRICE_ALERT,
				"Alert",
				"Message body",
				null);

		assertEquals(NotificationType.PRICE_ALERT, created.getType());
		assertEquals("Alert", created.getTitle());
		assertEquals("Message body", created.getBody());
		assertFalse(created.isRead());
		assertEquals(DeliveryStatus.PENDING, created.getStatus());
		assertEquals(user, created.getUser());
	}

	@Test
	void createNotificationLinksReferenceIdToAlert() {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		PriceAlert alert = new PriceAlert();
		setField(alert, "id", alertId);
		when(userRepository.getReferenceById(userId)).thenReturn(userReference(userId));
		when(priceAlertRepository.getReferenceById(alertId)).thenReturn(alert);
		when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Notification created = notificationService.createNotification(
				userId,
				NotificationType.PRICE_ALERT,
				"T",
				"M",
				alertId);

		assertEquals(alert, created.getAlert());
	}

	@Test
	void markReadSetsReadAtForUnreadNotification() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		Notification notification = unreadNotification(userId, notificationId);
		when(notificationRepository.findByIdAndUser_Id(notificationId, userId)).thenReturn(Optional.of(notification));
		when(notificationRepository.save(notification)).thenReturn(notification);

		NotificationResponse response = notificationService.markRead(userId, notificationId);

		assertTrue(response.isRead());
		assertTrue(notification.isRead());
		assertTrue(notification.getReadAt() != null);
		verify(notificationRepository).save(notification);
	}

	@Test
	void markReadPreservesReadAtWhenAlreadyRead() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		Instant originalReadAt = Instant.parse("2026-10-10T12:00:00Z");
		Notification notification = unreadNotification(userId, notificationId);
		notification.setRead(true);
		notification.setReadAt(originalReadAt);
		when(notificationRepository.findByIdAndUser_Id(notificationId, userId)).thenReturn(Optional.of(notification));

		NotificationResponse response = notificationService.markRead(userId, notificationId);

		assertEquals(originalReadAt, response.readAt());
		verify(notificationRepository, never()).save(any());
	}

	@Test
	void markAllReadReturnsUpdatedCount() {
		UUID userId = UUID.randomUUID();
		when(notificationRepository.markAllUnreadAsRead(eq(userId), any(Instant.class))).thenReturn(2);

		MarkAllReadResponse response = notificationService.markAllRead(userId);

		assertEquals(2, response.updatedCount());
	}

	@Test
	void getThrowsWhenNotOwned() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(notificationRepository.findByIdAndUser_Id(notificationId, userId)).thenReturn(Optional.empty());

		assertThrows(NotificationNotFoundException.class, () -> notificationService.get(userId, notificationId));
	}

	@Test
	void deleteThrowsWhenNotOwned() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(notificationRepository.deleteByIdAndUser_Id(notificationId, userId)).thenReturn(0);

		assertThrows(NotificationNotFoundException.class, () -> notificationService.delete(userId, notificationId));
	}

	@Test
	void listUsesUnreadRepositoryWhenFiltered() {
		UUID userId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 20);
		Notification notification = unreadNotification(userId, UUID.randomUUID());
		when(notificationRepository.findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId, pageable))
				.thenReturn(new PageImpl<>(List.of(notification)));

		var page = notificationService.list(userId, true, pageable);

		assertEquals(1, page.getTotalElements());
		verify(notificationRepository).findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId, pageable);
		verify(notificationRepository, never()).findByUser_IdOrderByCreatedAtDesc(any(), any());
	}

	private static User userReference(UUID userId) {
		User user = new User();
		setField(user, "id", userId);
		return user;
	}

	private static Notification unreadNotification(UUID userId, UUID notificationId) {
		Notification notification = new Notification();
		setField(notification, "id", notificationId);
		notification.setUser(userReference(userId));
		notification.setType(NotificationType.PRICE_ALERT);
		notification.setTitle("Title");
		notification.setBody("Body");
		notification.setStatus(DeliveryStatus.PENDING);
		setField(notification, "createdAt", Instant.parse("2026-10-12T04:00:00Z"));
		return notification;
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
				throw new RuntimeException(ex);
			}
		}
		throw new IllegalArgumentException("Field not found: " + name);
	}

}
