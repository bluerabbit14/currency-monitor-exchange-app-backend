package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\+[1-9]\\d{7,14}$")
		String phoneNumber,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
		String otp
) {
}
