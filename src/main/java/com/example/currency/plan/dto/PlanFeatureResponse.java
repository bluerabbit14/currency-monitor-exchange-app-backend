package com.example.currency.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One feature of a plan")
public record PlanFeatureResponse(
		@Schema(example = "max_active_alerts") String key,
		@Schema(description = "Whether the plan includes this feature", example = "true") boolean enabled,
		@Schema(description = "Usage cap; null means unlimited when enabled", example = "5", nullable = true) Integer limit
) {
}
