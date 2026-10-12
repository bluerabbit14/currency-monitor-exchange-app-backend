package com.example.currency.notification.controller;

import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.notification.dto.MarkAllReadResponse;
import com.example.currency.notification.dto.NotificationResponse;
import com.example.currency.notification.service.NotificationService;
import com.example.currency.web.PaginationParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
@Tag(name = "Notifications", description = "Signed-in user's notification inbox")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

	private final TokenService tokenService;
	private final NotificationService notificationService;

	public NotificationController(TokenService tokenService, NotificationService notificationService) {
		this.tokenService = tokenService;
		this.notificationService = notificationService;
	}

	@GetMapping
	@Operation(
			summary = "List notifications",
			description = """
					Returns notifications for the signed-in user, newest first.
					Pages are zero-based and size is capped at 100.
					Set unreadOnly=true to return only unread notifications.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Notification page",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							value = """
									{
									  "content": [
									    {
									      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									      "type": "PRICE_ALERT",
									      "title": "BTC/USD alert",
									      "message": "BTC/USD crossed your target price.",
									      "referenceId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
									      "isRead": false,
									      "createdAt": "2026-10-12T04:00:00Z",
									      "readAt": null
									    }
									  ],
									  "page": {
									    "size": 20,
									    "number": 0,
									    "totalElements": 1,
									    "totalPages": 1
									  }
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
	@ApiResponse(responseCode = "400", description = "Invalid page or size")
	public ResponseEntity<Page<NotificationResponse>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "When true, return only unread notifications")
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@ParameterObject
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			HttpServletRequest request) {
		PaginationParams.validate(request.getParameter("page"), request.getParameter("size"));
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(notificationService.list(userId, unreadOnly, pageable));
	}

	@GetMapping("/{notificationId}")
	@Operation(summary = "Get one notification", description = "Returns a notification owned by the signed-in user.")
	@ApiResponse(
			responseCode = "200",
			description = "Notification",
			content = @Content(schema = @Schema(implementation = NotificationResponse.class))
	)
	@ApiResponse(
			responseCode = "404",
			description = "Notification not found",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Not Found",
									  "status": 404,
									  "detail": "Notification not found"
									}
									"""
					)
			)
	)
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "400", description = "The notificationId is not a UUID")
	public ResponseEntity<NotificationResponse> get(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Notification id") @PathVariable UUID notificationId) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(notificationService.get(userId, notificationId));
	}

	@PatchMapping("/read-all")
	@Operation(
			summary = "Mark all notifications as read",
			description = """
					Marks every unread notification for the signed-in user as read.
					Already-read notifications are unchanged. Safe to repeat.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Bulk mark result",
			content = @Content(schema = @Schema(implementation = MarkAllReadResponse.class))
	)
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	public ResponseEntity<MarkAllReadResponse> markAllRead(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(notificationService.markAllRead(userId));
	}

	@PatchMapping("/{notificationId}/read")
	@Operation(
			summary = "Mark one notification as read",
			description = """
					Marks the notification as read for the signed-in user.
					If already read, readAt is preserved (idempotent).
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Updated notification",
			content = @Content(schema = @Schema(implementation = NotificationResponse.class))
	)
	@ApiResponse(responseCode = "404", description = "Notification not found")
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "400", description = "The notificationId is not a UUID")
	public ResponseEntity<NotificationResponse> markRead(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Notification id") @PathVariable UUID notificationId) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(notificationService.markRead(userId, notificationId));
	}

	@DeleteMapping("/{notificationId}")
	@Operation(
			summary = "Delete a notification",
			description = "Permanently removes a notification from the signed-in user's inbox."
	)
	@ApiResponse(responseCode = "204", description = "Notification deleted")
	@ApiResponse(responseCode = "404", description = "Notification not found")
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "400", description = "The notificationId is not a UUID")
	public ResponseEntity<Void> delete(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Notification id") @PathVariable UUID notificationId) {
		UUID userId = tokenService.requireUserId(authorization);
		notificationService.delete(userId, notificationId);
		return ResponseEntity.noContent().build();
	}

}
