package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
		@NotBlank(message = "Contact is required")
		String contact,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
		String otp
) {
}
