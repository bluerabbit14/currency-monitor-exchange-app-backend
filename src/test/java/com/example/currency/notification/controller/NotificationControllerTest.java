package com.example.currency.notification.controller;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.config.WebDataConfig;
import com.example.currency.notification.dto.MarkAllReadResponse;
import com.example.currency.notification.dto.NotificationResponse;
import com.example.currency.notification.enums.NotificationType;
import com.example.currency.notification.exception.NotificationNotFoundException;
import com.example.currency.notification.service.NotificationService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class, properties = "spring.data.web.pageable.max-page-size=100")
@Import({ GlobalExceptionHandler.class, WebDataConfig.class })
class NotificationControllerTest {

	private static final String AUTH = "Bearer test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void listsNotificationsForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.list(eq(userId), eq(false), any()))
				.thenReturn(new PageImpl<>(List.of(response(notificationId))));

		mockMvc.perform(get("/api/v1/users/me/notifications")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].id").value(notificationId.toString()))
				.andExpect(jsonPath("$.body.content[0].message").value("Hello"));

		verify(notificationService).list(eq(userId), eq(false), any());
	}

	@Test
	void listsUnreadOnlyWhenRequested() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.list(eq(userId), eq(true), any())).thenReturn(new PageImpl<>(List.of()));

		mockMvc.perform(get("/api/v1/users/me/notifications")
						.param("unreadOnly", "true")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk());

		verify(notificationService).list(eq(userId), eq(true), any());
	}

	@Test
	void getsOwnedNotification() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.get(userId, notificationId)).thenReturn(response(notificationId));

		mockMvc.perform(get("/api/v1/users/me/notifications/{notificationId}", notificationId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(notificationId.toString()));
	}

	@Test
	void markReadEndpoint() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.markRead(userId, notificationId))
				.thenReturn(new NotificationResponse(
						notificationId,
						NotificationType.PRICE_ALERT,
						"T",
						"M",
						null,
						true,
						Instant.parse("2026-10-12T04:00:00Z"),
						Instant.parse("2026-10-12T05:00:00Z")));

		mockMvc.perform(patch("/api/v1/users/me/notifications/{notificationId}/read", notificationId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.isRead").value(true));
	}

	@Test
	void markAllReadUsesDedicatedRoute() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.markAllRead(userId)).thenReturn(new MarkAllReadResponse(3));

		mockMvc.perform(patch("/api/v1/users/me/notifications/read-all")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.updatedCount").value(3));

		verify(notificationService).markAllRead(userId);
	}

	@Test
	void deletesOwnedNotification() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);

		mockMvc.perform(delete("/api/v1/users/me/notifications/{notificationId}", notificationId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNoContent());

		verify(notificationService).delete(userId, notificationId);
	}

	@Test
	void rejectsUnauthenticatedRequests() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get("/api/v1/users/me/notifications"))
				.andExpect(status().isUnauthorized());

		verify(notificationService, never()).list(any(), eq(false), any());
	}

	@Test
	void returnsNotFoundWhenNotificationMissing() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(notificationService.get(userId, notificationId)).thenThrow(new NotificationNotFoundException(notificationId));

		mockMvc.perform(get("/api/v1/users/me/notifications/{notificationId}", notificationId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Notification not found"));
	}

	private static NotificationResponse response(UUID id) {
		return new NotificationResponse(
				id,
				NotificationType.PRICE_ALERT,
				"T",
				"Hello",
				null,
				false,
				Instant.parse("2026-10-12T04:00:00Z"),
				null);
	}

}
