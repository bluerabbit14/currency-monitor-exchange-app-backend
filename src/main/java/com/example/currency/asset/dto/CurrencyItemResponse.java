package com.example.currency.asset.dto;

import com.example.currency.asset.enums.AssetStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "An active asset in the currency catalog, flagged when the caller has favorited it")
public record CurrencyItemResponse(
		@Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,
		@Schema(example = "BTC") String symbol,
		@Schema(example = "Bitcoin") String name,
		@Schema(description = "True when the signed-in user has favorited this asset; always false when anonymous",
				example = "true") boolean favorite,
		@Schema(example = "ACTIVE") AssetStatus status
) {
}
