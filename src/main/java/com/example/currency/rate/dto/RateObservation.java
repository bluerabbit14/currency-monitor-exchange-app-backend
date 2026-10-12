package com.example.currency.rate.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RateObservation(
		UUID pairId,
		BigDecimal rate,
		Instant observedAt,
		Instant fetchedAt,
		String providerCode,
		String providerObservationId
) {
}
