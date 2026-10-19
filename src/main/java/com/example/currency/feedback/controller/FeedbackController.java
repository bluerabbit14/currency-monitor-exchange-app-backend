package com.example.currency.feedback.controller;

import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.feedback.dto.FeedbackResponse;
import com.example.currency.feedback.dto.SubmitFeedbackRequest;
import com.example.currency.feedback.service.FeedbackService;
import com.example.currency.web.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedback")
@Tag(name = "Feedback", description = "Ratings and feedback sent from the app")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class FeedbackController {

	private final TokenService tokenService;
	private final FeedbackService feedbackService;

	public FeedbackController(TokenService tokenService, FeedbackService feedbackService) {
		this.tokenService = tokenService;
		this.feedbackService = feedbackService;
	}

	@PostMapping
	@ApiMessage("Thanks for your feedback.")
	@Operation(
			summary = "Submit a rating or feedback",
			description = "Send a 1 to 5 rating, a message, or both. Requires the Bearer access token in the "
					+ "Authorization header; the feedback is linked to that user. Missing or invalid token returns 401.")
	public ResponseEntity<FeedbackResponse> submit(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody SubmitFeedbackRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(userId, request));
	}

}
