package com.example.currency.rate.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A validated provider price. {@code observedAt} and {@code observationId} are null when the provider
 * endpoint does not supply them; callers must not invent them.
 */
public record ProviderQuote(BigDecimal rate, Instant observedAt, String observationId) {
}
