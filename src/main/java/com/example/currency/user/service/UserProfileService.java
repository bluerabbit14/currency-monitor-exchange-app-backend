package com.example.currency.user.service;

import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.notification.repository.NotificationPreferenceRepository;
import com.example.currency.plan.dto.PlanResponse;
import com.example.currency.user.dto.UserPlanResponse;
import com.example.currency.asset.service.AssetService;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.repository.RefreshSessionRepository;
import com.example.currency.deletion.config.DeletionProperties;
import com.example.currency.deletion.dto.DeletionScheduleResponse;
import com.example.currency.plan.service.PlanService;
import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.enums.SystemLogStatus;
import com.example.currency.systemlog.service.SystemLogEvent;
import com.example.currency.systemlog.service.SystemLogService;
import com.example.currency.user.dto.UpdateUserProfileRequest;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class UserProfileService {

	private final UserRepository userRepository;
	private final RefreshSessionRepository refreshSessionRepository;

	private final DeletionProperties deletionProperties;
	private final SystemLogService systemLog;
	private final PlanService planService;
	private final AssetService assetService;
	private final NotificationPreferenceRepository notificationPreferenceRepository;

	public UserProfileService(
			UserRepository userRepository,
			RefreshSessionRepository refreshSessionRepository,
			DeletionProperties deletionProperties,
			SystemLogService systemLog,
			PlanService planService,
			AssetService assetService,
			NotificationPreferenceRepository notificationPreferenceRepository) {
		this.userRepository = userRepository;
		this.refreshSessionRepository = refreshSessionRepository;
		this.deletionProperties = deletionProperties;
		this.systemLog = systemLog;
		this.planService = planService;
		this.assetService = assetService;
		this.notificationPreferenceRepository = notificationPreferenceRepository;
	}

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(UUID userId) {
		return toResponse(requireActive(userId));
	}

	public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
		User user = requireActive(userId);
		boolean changed = false;
		if (request.displayName() != null) {
			user.setDisplayName(blankToNull(request.displayName()));
			changed = true;
		}
		if (request.profileImageUrl() != null) {
			user.setProfileImageUrl(blankToNull(request.profileImageUrl()));
			changed = true;
		}
		if (changed) {
			user = userRepository.save(user);
		}
		return toResponse(user);
	}

	/**
	 * Starts the grace period. The data stays intact until it ends; signing in again cancels the deletion
	 * and the purge job erases the account afterwards.
	 */
	public DeletionScheduleResponse deleteProfile(UUID userId) {
		User user = requireActive(userId);
		Instant now = Instant.now();
		Instant scheduledFor = now.plus(Duration.ofDays(deletionProperties.getGracePeriodDays()));
		user.scheduleDeletion(now, scheduledFor);
		userRepository.save(user);
		refreshSessionRepository.revokeActiveByUserId(userId, now);
		systemLog.log(SystemLogEvent.of(SystemEventType.ACCOUNT_DELETION_REQUESTED, SystemLogStatus.SUCCESS)
				.userId(userId)
				.meta("scheduledFor", scheduledFor.toString()));
		return new DeletionScheduleResponse(deletionProperties.getGracePeriodDays(), scheduledFor);
	}

	private User requireActive(UUID userId) {
		return userRepository.findById(userId)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(InvalidAccessTokenException::new);
	}

	private UserProfileResponse toResponse(User user) {
		return new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getPhoneNumber(),
				user.getDisplayName(),
				user.getProfileImageUrl(),
				toUserPlan(planService.currentPlanFor(user.getId())),
				user.getBaseAssetId() == null ? null : assetService.findActive(user.getBaseAssetId()).orElse(null),
				// Read-only lookup: a user who never touched the setting has no row and gets the default (on).
				notificationPreferenceRepository.findByUserId(user.getId())
						.map(NotificationPreference::isPushEnabled)
						.orElse(true));
	}

	private static UserPlanResponse toUserPlan(PlanResponse plan) {
		return plan == null ? null : new UserPlanResponse(plan.id(), plan.name());
	}

	private static String blankToNull(String value) {
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
