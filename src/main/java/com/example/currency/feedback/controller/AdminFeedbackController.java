package com.example.currency.feedback.controller;

import com.example.currency.auth.service.AdminKeyService;
import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.dto.UpdateFeedbackStatusRequest;
import com.example.currency.feedback.service.FeedbackService;
import com.example.currency.web.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@Tag(name = "Admin", description = "Back-office endpoints, protected by the X-Admin-Key header")
public class AdminFeedbackController {

	private final AdminKeyService adminKeyService;
	private final FeedbackService feedbackService;

	public AdminFeedbackController(AdminKeyService adminKeyService, FeedbackService feedbackService) {
		this.adminKeyService = adminKeyService;
		this.feedbackService = feedbackService;
	}

	@PatchMapping("/{id}/status")
	@ApiMessage("Feedback status updated.")
	@Operation(
			summary = "Update the resolution status of a piece of feedback",
			description = "Moves feedback between NEW, REVIEWED and RESOLVED. Requires the X-Admin-Key header; "
					+ "returns 403 when it is missing or wrong, or when no admin key is configured.")
	public ResponseEntity<FeedbackResponse> updateStatus(
			@Parameter(description = "Admin API key", required = true)
			@RequestHeader(value = AdminKeyService.HEADER, required = false) String adminKey,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateFeedbackStatusRequest request) {
		adminKeyService.requireAdmin(adminKey);
		return ResponseEntity.ok(feedbackService.updateStatus(id, request.status()));
	}

}
