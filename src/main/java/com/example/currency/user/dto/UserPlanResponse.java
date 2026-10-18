package com.example.currency.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "The plan a user is on. Feature entitlements are served by the plans endpoint")
public record UserPlanResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "Free") String name
) {
}
