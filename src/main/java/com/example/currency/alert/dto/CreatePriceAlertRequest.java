package com.example.currency.alert.dto;

import com.example.currency.alert.enums.AlertCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Create a price alert for an active configured asset pair")
public record CreatePriceAlertRequest(
		@Schema(description = "Base asset symbol", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "base must not be blank")
		String base,
		@Schema(description = "Quote asset symbol", example = "INR", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "quote must not be blank")
		String quote,
		@Schema(
				description = "Trigger condition",
				example = "ABOVE",
				allowableValues = {"ABOVE", "BELOW"},
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotNull(message = "condition is required")
		AlertCondition condition,
		@Schema(
				description = "Target rate; must be positive",
				example = "90.00",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		@NotNull(message = "targetRate is required")
		@DecimalMin(value = "0", inclusive = false, message = "targetRate must be positive")
		BigDecimal targetRate
) {
}
