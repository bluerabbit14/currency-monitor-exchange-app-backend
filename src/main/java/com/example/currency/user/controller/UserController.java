package com.example.currency.user.controller;

import com.example.currency.user.dto.SetBaseCurrencyRequest;
import org.springframework.web.bind.annotation.PutMapping;
import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.deletion.dto.DeletionScheduleResponse;
import com.example.currency.user.dto.UpdateUserProfileRequest;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

	private final TokenService tokenService;
	private final UserProfileService userProfileService;

	public UserController(TokenService tokenService, UserProfileService userProfileService) {
		this.tokenService = tokenService;
		this.userProfileService = userProfileService;
	}

	@GetMapping("/me")
	@Operation(summary = "Get the signed-in user's profile")
	public ResponseEntity<UserProfileResponse> getProfile(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(userProfileService.getProfile(userId));
	}

	@PatchMapping("/me")
	@Operation(summary = "Update the signed-in user's profile")
	public ResponseEntity<UserProfileResponse> updateProfile(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody UpdateUserProfileRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
	}

	@PutMapping("/me/base-currency")
	@Operation(
			summary = "Set the signed-in user's base currency",
			description = "Choose from GET /api/v1/currencies. Returns the updated profile. "
					+ "404 when the id is not an active currency; 400 when currencyId is missing or not a UUID.")
	public ResponseEntity<UserProfileResponse> setBaseCurrency(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody SetBaseCurrencyRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(userProfileService.setBaseCurrency(userId, request.currencyId()));
	}

	@DeleteMapping("/me")
	@Operation(
			summary = "Request deletion of the signed-in user's profile",
			description = """
					Starts a grace period (15 days by default). The user is signed out everywhere. Signing in again
					during the grace period cancels the deletion. After it, personal data, alerts, favorites and
					devices are permanently erased; payment records are kept.
					""")
	public ResponseEntity<DeletionScheduleResponse> deleteProfile(
			@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.accepted().body(userProfileService.deleteProfile(userId));
	}

}
