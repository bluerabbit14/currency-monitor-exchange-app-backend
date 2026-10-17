package com.example.currency.deletion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "When the profile will be permanently deleted")
public record DeletionScheduleResponse(
		@Schema(example = "15") int gracePeriodDays,
		@Schema(description = "Signing in again before this time cancels the deletion") Instant scheduledFor
) {
}
