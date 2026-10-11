package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpSendRequest(
		@NotBlank(message = "Phone number is required")
		@Pattern(
				regexp = "^\\+[1-9]\\d{7,14}$",
				message = "Enter a valid international phone number"
		)
		String phoneNumber
) {
}
