package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
		@NotBlank(message = "Contact is required")
		String contact
) {
}
