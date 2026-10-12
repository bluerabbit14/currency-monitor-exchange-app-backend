package com.example.currency.device.controller;

import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.device.dto.DeviceResponse;
import com.example.currency.device.dto.RegisterDeviceRequest;
import com.example.currency.device.dto.UpdateDeviceRequest;
import com.example.currency.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/devices")
@Tag(name = "Devices", description = "Android FCM device registration for the signed-in user")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DeviceController {

	private final TokenService tokenService;
	private final DeviceService deviceService;

	public DeviceController(TokenService tokenService, DeviceService deviceService) {
		this.tokenService = tokenService;
		this.deviceService = deviceService;
	}

	@PostMapping
	@Operation(
			summary = "Register or refresh an Android device",
			description = """
					Stores the FCM registration token for the signed-in user.
					Only ANDROID is supported. Repeating the same token refreshes lastSeenAt,
					reactivates an inactive registration, and reassigns the token if it previously
					belonged to another account. FCM tokens are never returned in responses.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Device registered or refreshed",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = DeviceResponse.class),
					examples = @ExampleObject(
							name = "Registered device",
							value = """
									{
									  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									  "platform": "ANDROID",
									  "deviceName": "My Android Phone",
									  "isActive": true,
									  "createdAt": "2026-10-12T04:00:00Z",
									  "updatedAt": "2026-10-12T04:00:00Z",
									  "lastSeenAt": "2026-10-12T04:00:00Z"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Validation failed or unsupported platform",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Bad Request",
									  "status": 400,
									  "detail": "platform must be ANDROID"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	public ResponseEntity<DeviceResponse> register(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody RegisterDeviceRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(deviceService.register(userId, request));
	}

	@GetMapping
	@Operation(
			summary = "List the signed-in user's devices",
			description = """
					Returns active and inactive device registrations for the signed-in user only,
					ordered by lastSeenAt descending. FCM tokens are never included.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Devices for the signed-in user",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							value = """
									[
									  {
									    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									    "platform": "ANDROID",
									    "deviceName": "My Android Phone",
									    "isActive": true,
									    "createdAt": "2026-10-12T04:00:00Z",
									    "updatedAt": "2026-10-12T05:00:00Z",
									    "lastSeenAt": "2026-10-12T05:00:00Z"
									  }
									]
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	public ResponseEntity<List<DeviceResponse>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(deviceService.list(userId));
	}

	@PatchMapping("/{deviceId}")
	@Operation(
			summary = "Update device metadata",
			description = """
					Updates permitted metadata for a device owned by the signed-in user.
					Only deviceName may be changed. Omitted fields remain unchanged; a blank
					deviceName clears the name. Token, platform, ownership, and timestamps
					cannot be changed through this endpoint. Reactivation requires re-registering
					the FCM token via POST.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Device updated",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = DeviceResponse.class)
			)
	)
	@ApiResponse(responseCode = "400", description = "Validation failed or deviceId is not a UUID")
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "404",
			description = "Device does not exist or is not owned by the signed-in user",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Not Found",
									  "status": 404,
									  "detail": "Device not found"
									}
									"""
					)
			)
	)
	public ResponseEntity<DeviceResponse> update(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Device id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID deviceId,
			@Valid @RequestBody UpdateDeviceRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(deviceService.update(userId, deviceId, request));
	}

	@DeleteMapping("/{deviceId}")
	@Operation(
			summary = "Unregister a device",
			description = """
					Soft-deactivates the device registration for the signed-in user by setting
					isActive to false. The database row and FCM token are retained. Repeating
					the request for an already inactive owned device still returns 204.
					"""
	)
	@ApiResponse(responseCode = "204", description = "Device deactivated, or already inactive")
	@ApiResponse(responseCode = "400", description = "deviceId is not a UUID")
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "404",
			description = "Device does not exist or is not owned by the signed-in user",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Not Found",
									  "status": 404,
									  "detail": "Device not found"
									}
									"""
					)
			)
	)
	public ResponseEntity<Void> unregister(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Device id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID deviceId) {
		UUID userId = tokenService.requireUserId(authorization);
		deviceService.unregister(userId, deviceId);
		return ResponseEntity.noContent().build();
	}

}
