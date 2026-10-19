package com.example.currency.feedback.service;

import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.dto.SubmitFeedbackRequest;
import com.example.currency.feedback.entity.AppFeedback;
import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import com.example.currency.feedback.exception.FeedbackNotFoundException;
import com.example.currency.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FeedbackService {

	private final FeedbackRepository feedbackRepository;

	public FeedbackService(FeedbackRepository feedbackRepository) {
		this.feedbackRepository = feedbackRepository;
	}

	/** {@code userId} is null when the feedback comes from a user who is not signed in. */
	@Transactional
	public FeedbackResponse submit(UUID userId, SubmitFeedbackRequest request) {
		AppFeedback feedback = new AppFeedback();
		feedback.setUserId(userId);
		feedback.setRating(request.rating() == null ? null : request.rating().shortValue());
		feedback.setCategory(request.category() == null ? FeedbackCategory.GENERAL : request.category());
		feedback.setMessage(blankToNull(request.message()));
		feedback.setAppVersion(blankToNull(request.appVersion()));
		feedback.setPlatform(request.platform() == null ? FeedbackPlatform.ANDROID : request.platform());
		return toResponse(feedbackRepository.save(feedback));
	}

	@Transactional
	public FeedbackResponse updateStatus(UUID id, FeedbackStatus status) {
		AppFeedback feedback = feedbackRepository.findById(id).orElseThrow(FeedbackNotFoundException::new);
		feedback.setStatus(status);
		return toResponse(feedbackRepository.saveAndFlush(feedback));
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static FeedbackResponse toResponse(AppFeedback feedback) {
		return new FeedbackResponse(
				feedback.getId(),
				feedback.getRating() == null ? null : feedback.getRating().intValue(),
				feedback.getCategory(),
				feedback.getMessage(),
				feedback.getAppVersion(),
				feedback.getPlatform(),
				feedback.getStatus(),
				feedback.getCreatedAt(),
				feedback.getUpdatedAt());
	}

}
