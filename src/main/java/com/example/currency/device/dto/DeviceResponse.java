package com.example.currency.device.dto;

import com.example.currency.user.enums.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Device registration owned by the signed-in user. FCM tokens are never included.")
public record DeviceResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "ANDROID") DevicePlatform platform,
		@Schema(example = "My Android Phone") String deviceName,
		@Schema(example = "true") boolean isActive,
		@Schema(example = "2026-10-12T04:00:00Z") Instant createdAt,
		@Schema(example = "2026-10-12T05:00:00Z") Instant updatedAt,
		@Schema(example = "2026-10-12T05:00:00Z") Instant lastSeenAt
) {
}
