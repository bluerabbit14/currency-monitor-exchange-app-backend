package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleLinkVerifyRequest(
		@NotBlank(message = "linkToken is required")
		@Size(max = 128, message = "linkToken is invalid")
		String linkToken,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
		String otp
) {
}
