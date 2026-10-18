package com.example.currency.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetBaseCurrencyRequest(
		@NotNull(message = "currencyId is required")
		@Schema(description = "Id of an active currency from GET /api/v1/currencies",
				example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
		UUID currencyId
) {
}
