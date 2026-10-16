package com.example.currency.auth.controller;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpSendRequest;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyRequest;
import com.example.currency.auth.dto.OtpVerifyResponse;
import com.example.currency.auth.dto.RefreshTokenRequest;
import com.example.currency.auth.service.OtpService;
import com.example.currency.auth.service.TokenService;
import com.example.currency.web.ApiMessage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

	private final OtpService otpService;
	private final TokenService tokenService;

	public AuthController(OtpService otpService, TokenService tokenService) {
		this.otpService = otpService;
		this.tokenService = tokenService;
	}

	@PostMapping("/otp/send")
	@ApiMessage("OTP sent successfully.")
	@Operation(summary = "Send a sign-in / sign-up OTP")
	public ResponseEntity<OtpSendResponse> sendOtp(@Valid @RequestBody OtpSendRequest request) {
		return ResponseEntity.ok(otpService.sendOtp(request.contact()));
	}

	@PostMapping("/otp/verify")
	@ApiMessage("OTP verified successfully.")
	@Operation(summary = "Verify the OTP and sign in",
			description = "Creates the account on first verification (newUser = true). Profile fields "
					+ "stay empty until the user or an OIDC login fills them in.")
	public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return ResponseEntity.ok(otpService.verifyOtp(request.contact(), request.otp()));
	}

	@PostMapping("/refresh")
	@ApiMessage("Token refreshed successfully.")
	public ResponseEntity<AuthTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
		AuthTokenResponse response = tokenService.refresh(request.refreshToken());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		tokenService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

}
