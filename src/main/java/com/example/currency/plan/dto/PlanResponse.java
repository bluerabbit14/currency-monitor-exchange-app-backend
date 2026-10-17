package com.example.currency.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "An available plan with its features")
public record PlanResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "FREE") String code,
		@Schema(example = "Free") String name,
		@Schema(description = "True for the plan new users start on", example = "true") boolean isDefault,
		List<PlanFeatureResponse> features
) {
}
