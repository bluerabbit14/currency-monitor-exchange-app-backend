package com.example.currency.notification.controller;

import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.notification.dto.NotificationPreferenceResponse;
import com.example.currency.notification.dto.UpdateNotificationPreferenceRequest;
import com.example.currency.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
@Tag(name = "Notification Preferences", description = "Signed-in user's notification channel preferences")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationPreferenceController {

	private final TokenService tokenService;
	private final NotificationPreferenceService preferenceService;

	public NotificationPreferenceController(
			TokenService tokenService,
			NotificationPreferenceService preferenceService) {
		this.tokenService = tokenService;
		this.preferenceService = preferenceService;
	}

	@GetMapping
	@Operation(
			summary = "Get notification preferences",
			description = """
					Returns the signed-in user's notification channel preferences.
					If no preferences record exists yet, default preferences are created and returned.
					Defaults: pushEnabled=true, inAppEnabled=true, emailEnabled=false.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Preferences returned or initialized",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = NotificationPreferenceResponse.class),
					examples = @ExampleObject(
							name = "Default preferences",
							value = """
									{
									  "pushEnabled": true,
									  "inAppEnabled": true,
									  "emailEnabled": false,
									  "createdAt": "2026-10-12T04:00:00Z",
									  "updatedAt": "2026-10-12T04:00:00Z"
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
	public ResponseEntity<NotificationPreferenceResponse> getPreferences(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(preferenceService.getOrCreate(userId));
	}

	@PatchMapping
	@Operation(
			summary = "Update notification preferences",
			description = """
					Partially updates the signed-in user's notification channel preferences.
					Omitted fields remain unchanged. At least one field must be provided.
					Does not send notifications; it only stores preference flags.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Preferences updated",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = NotificationPreferenceResponse.class),
					examples = @ExampleObject(
							name = "Updated preferences",
							value = """
									{
									  "pushEnabled": true,
									  "inAppEnabled": true,
									  "emailEnabled": true,
									  "createdAt": "2026-10-12T04:00:00Z",
									  "updatedAt": "2026-10-12T05:00:00Z"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Empty update or malformed body",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Bad Request",
									  "status": 400,
									  "detail": "Validation failed",
									  "errors": [
									    { "field": "request", "message": "At least one preference field must be provided" }
									  ]
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
	public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody UpdateNotificationPreferenceRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(preferenceService.update(userId, request));
	}

}
