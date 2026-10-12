package com.example.currency.notification.service;

import com.example.currency.alert.repository.PriceAlertRepository;
import com.example.currency.notification.dto.MarkAllReadResponse;
import com.example.currency.notification.dto.NotificationResponse;
import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.enums.DeliveryStatus;
import com.example.currency.notification.enums.NotificationType;
import com.example.currency.notification.exception.NotificationNotFoundException;
import com.example.currency.notification.repository.NotificationRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final PriceAlertRepository priceAlertRepository;

	public NotificationService(
			NotificationRepository notificationRepository,
			UserRepository userRepository,
			PriceAlertRepository priceAlertRepository) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.priceAlertRepository = priceAlertRepository;
	}

	@Transactional(readOnly = true)
	public Page<NotificationResponse> list(UUID userId, boolean unreadOnly, Pageable pageable) {
		Page<Notification> page = unreadOnly
				? notificationRepository.findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
				: notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
		return page.map(NotificationService::toResponse);
	}

	@Transactional(readOnly = true)
	public NotificationResponse get(UUID userId, UUID notificationId) {
		return toResponse(requireOwned(userId, notificationId));
	}

	@Transactional
	public NotificationResponse markRead(UUID userId, UUID notificationId) {
		Notification notification = requireOwned(userId, notificationId);
		if (!notification.isRead()) {
			notification.setRead(true);
			notification.setReadAt(Instant.now());
			notification = notificationRepository.save(notification);
		}
		return toResponse(notification);
	}

	@Transactional
	public MarkAllReadResponse markAllRead(UUID userId) {
		int updated = notificationRepository.markAllUnreadAsRead(userId, Instant.now());
		return new MarkAllReadResponse(updated);
	}

	@Transactional
	public void delete(UUID userId, UUID notificationId) {
		if (notificationRepository.deleteByIdAndUser_Id(notificationId, userId) == 0) {
			throw new NotificationNotFoundException(notificationId);
		}
	}

	@Transactional
	public Notification createNotification(
			UUID userId,
			NotificationType type,
			String title,
			String message,
			UUID referenceId) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title is required");
		}
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message is required");
		}
		if (type == null) {
			throw new IllegalArgumentException("type is required");
		}

		Notification notification = new Notification();
		notification.setUser(userRepository.getReferenceById(userId));
		notification.setType(type);
		notification.setTitle(title.trim());
		notification.setBody(message.trim());
		notification.setStatus(DeliveryStatus.PENDING);
		notification.setRead(false);
		if (referenceId != null) {
			notification.setAlert(priceAlertRepository.getReferenceById(referenceId));
		}
		return notificationRepository.save(notification);
	}

	private Notification requireOwned(UUID userId, UUID notificationId) {
		return notificationRepository.findByIdAndUser_Id(notificationId, userId)
				.orElseThrow(() -> new NotificationNotFoundException(notificationId));
	}

	static NotificationResponse toResponse(Notification notification) {
		UUID referenceId = notification.getAlert() != null ? notification.getAlert().getId() : null;
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getBody(),
				referenceId,
				notification.isRead(),
				notification.getCreatedAt(),
				notification.getReadAt());
	}

}
