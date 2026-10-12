package com.example.currency.rate.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RateResponse(
		String base,
		String quote,
		BigDecimal rate,
		Instant observedAt,
		Instant fetchedAt,
		boolean stale,
		String providerCode
) {
}
