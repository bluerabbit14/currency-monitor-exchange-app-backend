package com.example.currency.notification.service;

import com.example.currency.notification.dto.NotificationPreferenceResponse;
import com.example.currency.notification.dto.UpdateNotificationPreferenceRequest;
import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.notification.exception.EmptyNotificationPreferenceUpdateException;
import com.example.currency.notification.repository.NotificationPreferenceRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository preferenceRepository;
	private final UserRepository userRepository;

	public NotificationPreferenceService(
			NotificationPreferenceRepository preferenceRepository,
			UserRepository userRepository) {
		this.preferenceRepository = preferenceRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public NotificationPreferenceResponse getOrCreate(UUID userId) {
		return toResponse(findOrCreate(userId));
	}

	@Transactional
	public NotificationPreferenceResponse update(UUID userId, UpdateNotificationPreferenceRequest request) {
		if (request.pushEnabled() == null && request.inAppEnabled() == null && request.emailEnabled() == null) {
			throw new EmptyNotificationPreferenceUpdateException();
		}

		NotificationPreference preference = findOrCreate(userId);
		if (request.pushEnabled() != null) {
			preference.setPushEnabled(request.pushEnabled());
		}
		if (request.inAppEnabled() != null) {
			preference.setInAppEnabled(request.inAppEnabled());
		}
		if (request.emailEnabled() != null) {
			preference.setEmailEnabled(request.emailEnabled());
		}
		return toResponse(preferenceRepository.save(preference));
	}

	private NotificationPreference findOrCreate(UUID userId) {
		return preferenceRepository.findByUserId(userId)
				.orElseGet(() -> createDefaults(userId));
	}

	private NotificationPreference createDefaults(UUID userId) {
		NotificationPreference preference = new NotificationPreference();
		preference.setUser(userRepository.getReferenceById(userId));
		try {
			return preferenceRepository.saveAndFlush(preference);
		}
		catch (DataIntegrityViolationException ex) {
			return preferenceRepository.findByUserId(userId)
					.orElseThrow(() -> ex);
		}
	}

	private static NotificationPreferenceResponse toResponse(NotificationPreference preference) {
		return new NotificationPreferenceResponse(
				preference.isPushEnabled(),
				preference.isInAppEnabled(),
				preference.isEmailEnabled(),
				preference.getCreatedAt(),
				preference.getUpdatedAt());
	}

}
