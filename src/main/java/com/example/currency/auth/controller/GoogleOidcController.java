package com.example.currency.auth.controller;

import com.example.currency.auth.dto.GoogleLinkVerifyRequest;
import com.example.currency.auth.dto.GoogleOidcRequest;
import com.example.currency.auth.dto.GoogleOidcResponse;
import com.example.currency.auth.service.GoogleOidcService;
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
@RequestMapping("/api/v1/auth/oidc/google")
@Tag(name = "Auth")
public class GoogleOidcController {

	private final GoogleOidcService googleOidcService;

	public GoogleOidcController(GoogleOidcService googleOidcService) {
		this.googleOidcService = googleOidcService;
	}

	@PostMapping
	@ApiMessage("Google sign-in processed.")
	@Operation(summary = "Sign in with a Google ID token",
			description = "Verifies the ID token with Google (signature, issuer, audience, expiry). Status SIGNED_IN "
					+ "returns Yakku tokens (a new account is created when neither the Google identity nor the email "
					+ "is known). Status LINK_VERIFICATION_REQUIRED means the verified email already belongs to an "
					+ "account: an OTP was emailed to it and nothing is linked until /link/verify succeeds.")
	public ResponseEntity<GoogleOidcResponse> signIn(@Valid @RequestBody GoogleOidcRequest request) {
		return ResponseEntity.ok(googleOidcService.login(request.idToken()));
	}

	@PostMapping("/link/verify")
	@ApiMessage("Google account linked.")
	@Operation(summary = "Verify the emailed OTP, link Google to the existing account and sign in",
			description = "Takes the linkToken returned by the sign-in step and the OTP emailed to the existing "
					+ "account. The OTP is single use and attempt-limited; on any failure nothing is linked.")
	public ResponseEntity<GoogleOidcResponse> verifyLink(@Valid @RequestBody GoogleLinkVerifyRequest request) {
		return ResponseEntity.ok(googleOidcService.verifyLink(request.linkToken(), request.otp()));
	}

}
