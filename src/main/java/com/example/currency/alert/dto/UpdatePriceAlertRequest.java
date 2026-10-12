package com.example.currency.alert.dto;

import com.example.currency.alert.enums.AlertCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

@Schema(description = "Partial update of a price alert; omitted fields remain unchanged")
public record UpdatePriceAlertRequest(
		@Schema(
				description = "Trigger condition",
				example = "BELOW",
				allowableValues = {"ABOVE", "BELOW"}
		)
		AlertCondition condition,
		@Schema(description = "Target rate; must be positive when provided", example = "89.50")
		@DecimalMin(value = "0", inclusive = false, message = "targetRate must be positive")
		BigDecimal targetRate
) {
}
