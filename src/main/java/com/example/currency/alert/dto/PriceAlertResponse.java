package com.example.currency.alert.dto;

import com.example.currency.alert.enums.AlertCondition;
import com.example.currency.alert.enums.AlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A price alert owned by the signed-in user")
public record PriceAlertResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "USD") String base,
		@Schema(example = "INR") String quote,
		@Schema(example = "ABOVE", allowableValues = {"ABOVE", "BELOW"}) AlertCondition condition,
		@Schema(example = "90.00") BigDecimal targetRate,
		@Schema(example = "ACTIVE", allowableValues = {"ACTIVE", "TRIGGERED", "CANCELLED"}) AlertStatus status,
		@Schema(example = "2026-10-12T04:00:00Z") Instant createdAt,
		@Schema(example = "2026-10-12T04:00:00Z") Instant updatedAt,
		@Schema(description = "When the alert last triggered; null until evaluation runs", example = "null")
		Instant triggeredAt
) {
}
