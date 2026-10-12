package com.example.currency.rate.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RateHistoryResponse(
		BigDecimal rate,
		Instant observedAt,
		Instant fetchedAt,
		String providerCode
) {
}
