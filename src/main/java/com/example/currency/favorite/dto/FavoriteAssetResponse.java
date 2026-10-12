package com.example.currency.favorite.dto;

import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An asset the signed-in user has favorited")
public record FavoriteAssetResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID assetId,
		@Schema(example = "BTC") String symbol,
		@Schema(example = "Bitcoin") String name,
		@Schema(example = "CRYPTO", allowableValues = {"FIAT", "CRYPTO", "COMMODITY"}) AssetType type,
		@Schema(example = "ACTIVE") AssetStatus status,
		@Schema(description = "When the asset was favorited", example = "2026-10-12T04:00:00Z") Instant createdAt
) {
}
