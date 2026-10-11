package com.example.currency.auth.controller;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpSendRequest;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyRequest;
import com.example.currency.auth.dto.RefreshTokenRequest;
import com.example.currency.auth.service.OtpService;
import com.example.currency.auth.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	static final String OTP_SENT_MESSAGE =
			"If an account exists for this contact, an OTP has been sent.";

	private final OtpService otpService;
	private final TokenService tokenService;

	public AuthController(OtpService otpService, TokenService tokenService) {
		this.otpService = otpService;
		this.tokenService = tokenService;
	}

	@PostMapping("/otp/send")
	public ResponseEntity<OtpSendResponse> sendOtp(@Valid @RequestBody OtpSendRequest request) {
		otpService.sendOtp(request.contact());
		return ResponseEntity.ok(new OtpSendResponse(OTP_SENT_MESSAGE));
	}

	@PostMapping("/otp/verify")
	public ResponseEntity<AuthTokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		AuthTokenResponse response = otpService.verifyOtp(request.contact(), request.otp());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/refresh")
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
