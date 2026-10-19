package com.example.currency.feedback.service;

import com.example.currency.auth.config.AdminProperties;
import com.example.currency.auth.exception.AdminAccessDeniedException;
import com.example.currency.auth.service.AdminKeyService;
import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.dto.SubmitFeedbackRequest;
import com.example.currency.feedback.entity.AppFeedback;
import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import com.example.currency.feedback.exception.FeedbackNotFoundException;
import com.example.currency.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

	private final FeedbackRepository repository = mock(FeedbackRepository.class);
	private final FeedbackService service = new FeedbackService(repository);

	private AppFeedback saved() {
		when(repository.save(any(AppFeedback.class))).thenAnswer(call -> call.getArgument(0));
		return null;
	}

	@Test
	void appliesDefaultsAndTrimsText() {
		saved();
		UUID userId = UUID.randomUUID();

		FeedbackResponse response = service.submit(userId,
				new SubmitFeedbackRequest(5, null, "  Great  ", "  ", null));

		ArgumentCaptor<AppFeedback> captor = ArgumentCaptor.forClass(AppFeedback.class);
		verify(repository).save(captor.capture());
		AppFeedback feedback = captor.getValue();
		assertEquals(userId, feedback.getUserId());
		assertEquals((short) 5, feedback.getRating());
		assertEquals(FeedbackCategory.GENERAL, feedback.getCategory());
		assertEquals("Great", feedback.getMessage());
		assertNull(feedback.getAppVersion());
		assertEquals(FeedbackPlatform.ANDROID, feedback.getPlatform());
		assertEquals(FeedbackStatus.NEW, feedback.getStatus());
		assertEquals(5, response.rating());
	}

	@Test
	void anonymousFeedbackHasNoUser() {
		saved();

		service.submit(null, new SubmitFeedbackRequest(null, FeedbackCategory.BUG, "Crash", "1.0",
				FeedbackPlatform.IOS));

		ArgumentCaptor<AppFeedback> captor = ArgumentCaptor.forClass(AppFeedback.class);
		verify(repository).save(captor.capture());
		assertNull(captor.getValue().getUserId());
		assertNull(captor.getValue().getRating());
		assertEquals(FeedbackPlatform.IOS, captor.getValue().getPlatform());
	}

	@Test
	void updatesTheStatus() {
		UUID id = UUID.randomUUID();
		AppFeedback feedback = new AppFeedback();
		when(repository.findById(id)).thenReturn(Optional.of(feedback));
		when(repository.saveAndFlush(feedback)).thenReturn(feedback);

		FeedbackResponse response = service.updateStatus(id, FeedbackStatus.RESOLVED);

		assertEquals(FeedbackStatus.RESOLVED, feedback.getStatus());
		assertEquals(FeedbackStatus.RESOLVED, response.status());
	}

	@Test
	void unknownFeedbackIsNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		assertThrows(FeedbackNotFoundException.class, () -> service.updateStatus(id, FeedbackStatus.REVIEWED));
	}

	@Test
	void adminKeyMustMatchAndIsRequired() {
		AdminProperties configured = new AdminProperties();
		configured.setApiKey("secret");
		AdminKeyService guard = new AdminKeyService(configured);

		assertDoesNotThrow(() -> guard.requireAdmin("secret"));
		assertThrows(AdminAccessDeniedException.class, () -> guard.requireAdmin("wrong"));
		assertThrows(AdminAccessDeniedException.class, () -> guard.requireAdmin(null));

		// No key configured: closed to everyone, even a blank key.
		AdminKeyService closed = new AdminKeyService(new AdminProperties());
		assertThrows(AdminAccessDeniedException.class, () -> closed.requireAdmin(""));
		assertThrows(AdminAccessDeniedException.class, () -> closed.requireAdmin("secret"));
	}

}
