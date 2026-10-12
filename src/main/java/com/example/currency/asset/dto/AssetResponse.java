package com.example.currency.asset.dto;

import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "An active asset in the supported catalog")
public record AssetResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "BTC") String symbol,
		@Schema(example = "Bitcoin") String name,
		@Schema(example = "CRYPTO", allowableValues = {"FIAT", "CRYPTO", "COMMODITY"}) AssetType type,
		@Schema(example = "ACTIVE") AssetStatus status
) {
}
