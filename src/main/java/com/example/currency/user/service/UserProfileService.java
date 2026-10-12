package com.example.currency.user.service;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.repository.RefreshSessionRepository;
import com.example.currency.user.dto.UpdateUserProfileRequest;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class UserProfileService {

	private final UserRepository userRepository;
	private final RefreshSessionRepository refreshSessionRepository;

	public UserProfileService(UserRepository userRepository, RefreshSessionRepository refreshSessionRepository) {
		this.userRepository = userRepository;
		this.refreshSessionRepository = refreshSessionRepository;
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

	public void deleteProfile(UUID userId) {
		User user = requireActive(userId);
		user.setStatus(UserStatus.DELETED);
		userRepository.save(user);
		refreshSessionRepository.revokeActiveByUserId(userId, Instant.now());
	}

	private User requireActive(UUID userId) {
		return userRepository.findById(userId)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(InvalidAccessTokenException::new);
	}

	private static UserProfileResponse toResponse(User user) {
		return new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getPhoneNumber(),
				user.getDisplayName(),
				user.getProfileImageUrl(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}

	private static String blankToNull(String value) {
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
