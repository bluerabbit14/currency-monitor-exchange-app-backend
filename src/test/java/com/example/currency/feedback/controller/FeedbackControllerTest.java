package com.example.currency.feedback.controller;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.dto.SubmitFeedbackRequest;
import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import com.example.currency.feedback.service.FeedbackService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedbackController.class)
@Import(GlobalExceptionHandler.class)
class FeedbackControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FeedbackService feedbackService;

	@MockitoBean
	private TokenService tokenService;

	private static FeedbackResponse stored(Integer rating, String message) {
		Instant now = Instant.parse("2026-10-19T09:31:16Z");
		return new FeedbackResponse(UUID.randomUUID(), rating, FeedbackCategory.GENERAL, message, null,
				FeedbackPlatform.ANDROID, FeedbackStatus.NEW, now, now);
	}

	@Test
	void withoutABearerTokenItIsRejected() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(post("/api/v1/feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":5}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(feedbackService);
	}

	@Test
	void aBearerTokenLinksTheFeedbackToTheUser() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId("Bearer t")).thenReturn(userId);
		when(feedbackService.submit(any(), any())).thenReturn(stored(null, "Nice app"));

		mockMvc.perform(post("/api/v1/feedback")
						.header("Authorization", "Bearer t")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"Nice app\",\"category\":\"SUGGESTION\",\"platform\":\"IOS\","
								+ "\"appVersion\":\"1.4.2\"}"))
				.andExpect(status().isCreated());

		ArgumentCaptor<SubmitFeedbackRequest> captor = ArgumentCaptor.forClass(SubmitFeedbackRequest.class);
		verify(feedbackService).submit(org.mockito.ArgumentMatchers.eq(userId), captor.capture());
		assertEquals(FeedbackCategory.SUGGESTION, captor.getValue().category());
		assertEquals(FeedbackPlatform.IOS, captor.getValue().platform());
		assertEquals("1.4.2", captor.getValue().appVersion());
	}

	@Test
	void aTokenThatIsSentButInvalidIsRejected() throws Exception {
		when(tokenService.requireUserId("Bearer bad")).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(post("/api/v1/feedback")
						.header("Authorization", "Bearer bad")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":4}"))
				.andExpect(status().isUnauthorized());
		verify(feedbackService, never()).submit(any(), any());
	}

	@Test
	void aRatingOrAMessageIsRequired() throws Exception {
		mockMvc.perform(post("/api/v1/feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\":\"BUG\",\"message\":\"   \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verify(feedbackService, never()).submit(any(), any());
	}

	@Test
	void theRatingMustBeBetweenOneAndFive() throws Exception {
		for (String rating : new String[] {"0", "6", "-1"}) {
			mockMvc.perform(post("/api/v1/feedback")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"rating\":" + rating + "}"))
					.andExpect(status().isBadRequest());
		}
		verify(feedbackService, never()).submit(any(), any());
	}

	@Test
	void anUnknownCategoryIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/feedback")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":3,\"category\":\"COMPLAINT\"}"))
				.andExpect(status().isBadRequest());
	}

}
