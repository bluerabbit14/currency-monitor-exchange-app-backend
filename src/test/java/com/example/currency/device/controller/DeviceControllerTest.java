package com.example.currency.device.controller;

import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.device.dto.DeviceResponse;
import com.example.currency.device.dto.RegisterDeviceRequest;
import com.example.currency.device.dto.UpdateDeviceRequest;
import com.example.currency.device.exception.DeviceNotFoundException;
import com.example.currency.device.service.DeviceService;
import com.example.currency.user.enums.DevicePlatform;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceController.class)
@Import(GlobalExceptionHandler.class)
class DeviceControllerTest {

	private static final String AUTH = "Bearer test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeviceService deviceService;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void registersDeviceForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(deviceService.register(eq(userId), any(RegisterDeviceRequest.class)))
				.thenReturn(response(deviceId, "My Android Phone", true));

		mockMvc.perform(post("/api/v1/users/me/devices")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "example-fcm-registration-token",
								  "platform": "ANDROID",
								  "deviceName": "My Android Phone"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(deviceId.toString()))
				.andExpect(jsonPath("$.body.platform").value("ANDROID"))
				.andExpect(jsonPath("$.body.deviceName").value("My Android Phone"))
				.andExpect(jsonPath("$.body.isActive").value(true))
				.andExpect(jsonPath("$.body.fcmToken").doesNotExist())
				.andExpect(jsonPath("$.body.deviceToken").doesNotExist());

		verify(deviceService).register(eq(userId), any(RegisterDeviceRequest.class));
	}

	@Test
	void rejectsBlankToken() throws Exception {
		when(tokenService.requireUserId(AUTH)).thenReturn(UUID.randomUUID());

		mockMvc.perform(post("/api/v1/users/me/devices")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "   ",
								  "platform": "ANDROID"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.title").value("Bad Request"));

		verify(deviceService, never()).register(any(), any());
	}

	@Test
	void rejectsUnsupportedPlatform() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(deviceService.register(eq(userId), any(RegisterDeviceRequest.class)))
				.thenThrow(new IllegalArgumentException("platform must be ANDROID"));

		mockMvc.perform(post("/api/v1/users/me/devices")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "example-fcm-registration-token",
								  "platform": "IOS"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("platform must be ANDROID"));
	}

	@Test
	void listsDevicesWithoutExposingTokens() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(deviceService.list(userId)).thenReturn(List.of(response(deviceId, "Phone", true)));

		mockMvc.perform(get("/api/v1/users/me/devices")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body[0].id").value(deviceId.toString()))
				.andExpect(jsonPath("$.body[0].fcmToken").doesNotExist())
				.andExpect(jsonPath("$.body[0].deviceToken").doesNotExist());

		verify(deviceService).list(userId);
	}

	@Test
	void updatesDeviceMetadata() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(deviceService.update(eq(userId), eq(deviceId), any(UpdateDeviceRequest.class)))
				.thenReturn(response(deviceId, "Kitchen tablet", true));

		mockMvc.perform(patch("/api/v1/users/me/devices/{deviceId}", deviceId)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "deviceName": "Kitchen tablet",
								  "fcmToken": "should-be-ignored",
								  "platform": "IOS",
								  "isActive": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.deviceName").value("Kitchen tablet"))
				.andExpect(jsonPath("$.body.fcmToken").doesNotExist());

		verify(deviceService).update(eq(userId), eq(deviceId), eq(new UpdateDeviceRequest("Kitchen tablet")));
	}

	@Test
	void rejectsUpdateForMissingDevice() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(deviceService.update(eq(userId), eq(deviceId), any(UpdateDeviceRequest.class)))
				.thenThrow(new DeviceNotFoundException(deviceId));

		mockMvc.perform(patch("/api/v1/users/me/devices/{deviceId}", deviceId)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"deviceName\":\"Nope\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Device not found"));
	}

	@Test
	void unregistersDevice() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);

		mockMvc.perform(delete("/api/v1/users/me/devices/{deviceId}", deviceId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNoContent());

		verify(deviceService).unregister(userId, deviceId);
	}

	@Test
	void rejectsInvalidDeviceId() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me/devices/{deviceId}", "not-a-uuid")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isBadRequest());

		verify(deviceService, never()).unregister(any(), any());
	}

	@Test
	void rejectsUnauthenticatedRequests() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get("/api/v1/users/me/devices"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.title").value("Unauthorized"))
				.andExpect(jsonPath("$.error.message").value("Invalid or expired access token"));

		verify(deviceService, never()).list(any());
	}

	private static DeviceResponse response(UUID id, String deviceName, boolean active) {
		Instant at = Instant.parse("2026-10-12T04:00:00Z");
		return new DeviceResponse(id, DevicePlatform.ANDROID, deviceName, active, at, at, at);
	}

}
