package com.example.currency.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleOidcRequest(
		@NotBlank(message = "idToken is required")
		@Size(max = 8192, message = "idToken is too long")
		String idToken
) {
}
