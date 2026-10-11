package com.example.currency.auth.controller;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpSendRequest;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyRequest;
import com.example.currency.auth.dto.RefreshTokenRequest;
import com.example.currency.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/otp/send")
	public ResponseEntity<OtpSendResponse> sendOtp(@Valid @RequestBody OtpSendRequest request) {
		OtpSendResponse response = authService.sendOtp(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/otp/verify")
	public ResponseEntity<AuthTokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		AuthTokenResponse response = authService.verifyOtp(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
		AuthTokenResponse response = authService.refreshToken(request.refreshToken());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

}
