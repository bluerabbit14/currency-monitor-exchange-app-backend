package com.example.currency.notification.controller;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.notification.dto.NotificationPreferenceResponse;
import com.example.currency.notification.dto.UpdateNotificationPreferenceRequest;
import com.example.currency.notification.exception.EmptyNotificationPreferenceUpdateException;
import com.example.currency.notification.service.NotificationPreferenceService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationPreferenceController.class)
@Import(GlobalExceptionHandler.class)
class NotificationPreferenceControllerTest {

	private static final String AUTH = "Bearer test-token";
	private static final String BASE = "/api/v1/users/me/notification-preferences";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationPreferenceService preferenceService;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void getReturnsPreferencesForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.getOrCreate(userId)).thenReturn(response(true, true, false));

		mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pushEnabled").value(true))
				.andExpect(jsonPath("$.inAppEnabled").value(true))
				.andExpect(jsonPath("$.emailEnabled").value(false))
				.andExpect(jsonPath("$.createdAt").value("2026-10-12T04:00:00Z"));

		verify(preferenceService).getOrCreate(userId);
	}

	@Test
	void patchUpdatesEmailOnly() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.update(eq(userId), any(UpdateNotificationPreferenceRequest.class)))
				.thenReturn(response(true, true, true));

		mockMvc.perform(patch(BASE)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"emailEnabled": true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(true));

		verify(preferenceService).update(eq(userId), any(UpdateNotificationPreferenceRequest.class));
	}

	@Test
	void patchUpdatesMultipleFields() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.update(eq(userId), any(UpdateNotificationPreferenceRequest.class)))
				.thenReturn(response(false, true, false));

		mockMvc.perform(patch(BASE)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pushEnabled": false, "inAppEnabled": true, "emailEnabled": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pushEnabled").value(false))
				.andExpect(jsonPath("$.inAppEnabled").value(true))
				.andExpect(jsonPath("$.emailEnabled").value(false));
	}

	@Test
	void emptyPatchIsRejected() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.update(eq(userId), any(UpdateNotificationPreferenceRequest.class)))
				.thenThrow(new EmptyNotificationPreferenceUpdateException());

		mockMvc.perform(patch(BASE)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.detail").value("Validation failed"))
				.andExpect(jsonPath("$.errors[0].field").value("request"))
				.andExpect(jsonPath("$.errors[0].message")
						.value("At least one preference field must be provided"));
	}

	@Test
	void explicitNullFieldsAreRejectedAsEmptyUpdate() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.update(eq(userId), any(UpdateNotificationPreferenceRequest.class)))
				.thenThrow(new EmptyNotificationPreferenceUpdateException());

		mockMvc.perform(patch(BASE)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pushEnabled": null, "inAppEnabled": null, "emailEnabled": null}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("request"));
	}

	@Test
	void rejectsUnauthenticatedGet() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get(BASE))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.detail").value("Invalid or expired access token"));

		verify(preferenceService, never()).getOrCreate(any());
	}

	@Test
	void rejectsUnauthenticatedPatch() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(patch(BASE)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"emailEnabled": true}
								"""))
				.andExpect(status().isUnauthorized());

		verify(preferenceService, never()).update(any(), any());
	}

	@Test
	void usesAuthenticatedUserIdNotClientSuppliedIdentity() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(preferenceService.update(eq(userId), any(UpdateNotificationPreferenceRequest.class)))
				.thenReturn(response(true, true, true));

		mockMvc.perform(patch(BASE)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"emailEnabled": true, "userId": "00000000-0000-0000-0000-000000000099"}
								"""))
				.andExpect(status().isOk());

		verify(preferenceService).update(eq(userId), any(UpdateNotificationPreferenceRequest.class));
		verify(preferenceService, never()).update(eq(UUID.fromString("00000000-0000-0000-0000-000000000099")), any());
	}

	private static NotificationPreferenceResponse response(
			boolean pushEnabled,
			boolean inAppEnabled,
			boolean emailEnabled) {
		Instant at = Instant.parse("2026-10-12T04:00:00Z");
		return new NotificationPreferenceResponse(pushEnabled, inAppEnabled, emailEnabled, at, at);
	}

}
