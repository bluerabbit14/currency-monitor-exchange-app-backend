package com.example.currency.feedback.controller;

import com.example.currency.auth.exception.AdminAccessDeniedException;
import com.example.currency.auth.service.AdminKeyService;
import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import com.example.currency.feedback.exception.FeedbackNotFoundException;
import com.example.currency.feedback.service.FeedbackService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminFeedbackController.class)
@Import(GlobalExceptionHandler.class)
class AdminFeedbackControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FeedbackService feedbackService;

	@MockitoBean
	private AdminKeyService adminKeyService;

	private final UUID id = UUID.randomUUID();

	private static FeedbackResponse resolved(UUID id) {
		Instant now = Instant.parse("2026-10-19T09:31:16Z");
		return new FeedbackResponse(id, 4, FeedbackCategory.BUG, "Crash", "1.0", FeedbackPlatform.ANDROID,
				FeedbackStatus.RESOLVED, now, now);
	}

	@Test
	void updatesTheStatus() throws Exception {
		when(feedbackService.updateStatus(id, FeedbackStatus.RESOLVED)).thenReturn(resolved(id));

		mockMvc.perform(patch("/api/v1/admin/feedback/{id}/status", id)
						.header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RESOLVED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(id.toString()))
				.andExpect(jsonPath("$.body.status").value("RESOLVED"));
		verify(adminKeyService).requireAdmin("secret");
	}

	@Test
	void withoutAValidAdminKeyNothingIsChanged() throws Exception {
		doThrow(new AdminAccessDeniedException()).when(adminKeyService).requireAdmin(any());

		mockMvc.perform(patch("/api/v1/admin/feedback/{id}/status", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RESOLVED\"}"))
				.andExpect(status().isForbidden());
		verify(feedbackService, never()).updateStatus(any(), any());
	}

	@Test
	void unknownFeedbackIsNotFound() throws Exception {
		when(feedbackService.updateStatus(id, FeedbackStatus.REVIEWED)).thenThrow(new FeedbackNotFoundException());

		mockMvc.perform(patch("/api/v1/admin/feedback/{id}/status", id)
						.header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"REVIEWED\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsABadStatusOrId() throws Exception {
		mockMvc.perform(patch("/api/v1/admin/feedback/{id}/status", id)
						.header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/v1/admin/feedback/{id}/status", id)
						.header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/v1/admin/feedback/not-a-uuid/status")
						.header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"NEW\"}"))
				.andExpect(status().isBadRequest());
		verify(feedbackService, never()).updateStatus(any(), any());
	}

}
