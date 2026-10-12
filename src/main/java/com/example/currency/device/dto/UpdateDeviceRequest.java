package com.example.currency.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial update for a device owned by the signed-in user. Omitted fields are left unchanged.")
public record UpdateDeviceRequest(
		@Schema(description = "Optional display name. Blank clears the name.", example = "Kitchen tablet")
		@Size(max = 120, message = "Device name must be at most 120 characters")
		String deviceName
) {
}
