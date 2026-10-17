package com.example.currency.rate.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Prices one asset in another through USD, using stored FX rates. Pure BigDecimal arithmetic.
 */
final class FiatConversion {

	/** Scale of the NUMERIC(28, 12) rate columns, used when the display scale would round a price to zero. */
	static final int FALLBACK_SCALE = 12;

	private FiatConversion() {
	}

	/**
	 * @param usdPrice        price of one unit of the asset in USD
	 * @param fxRate          the stored FX rate, strictly positive
	 * @param fxIsQuotePerUsd true for a {@code USD/XXX} pair (XXX per 1 USD, multiply); false for
	 *                        {@code XXX/USD} (USD per 1 XXX, divide)
	 * @param displayScale    decimal places of the target fiat currency
	 */
	static BigDecimal usdToFiat(BigDecimal usdPrice, BigDecimal fxRate, boolean fxIsQuotePerUsd, int displayScale) {
		if (usdPrice == null || fxRate == null) {
			throw new IllegalArgumentException("price and fx rate are required");
		}
		return cross(usdPrice, true, fxRate, !fxIsQuotePerUsd, displayScale);
	}

	/**
	 * Price of one base unit in quote units: (USD value of base) / (USD value of quote).
	 *
	 * @param baseRate         stored rate of the base's USD pair, or null when the base is USD itself
	 * @param baseUsdPerUnit   true when that pair is {@code BASE/USD} (the rate is USD per unit), false when it is
	 *                         {@code USD/BASE} (the rate is units per USD)
	 * @param quoteRate        the same for the quote side; null when the quote is USD
	 * @param quoteUsdPerUnit  as {@code baseUsdPerUnit}, for the quote
	 * @param displayScale     decimal places of the quote currency
	 */
	static BigDecimal cross(BigDecimal baseRate, boolean baseUsdPerUnit,
							BigDecimal quoteRate, boolean quoteUsdPerUnit, int displayScale) {
		requirePositiveOrAbsent(baseRate);
		requirePositiveOrAbsent(quoteRate);
		// base USD value = nb / db, quote USD value = nq / dq, so price = (nb * dq) / (db * nq).
		BigDecimal nb = baseRate == null || !baseUsdPerUnit ? BigDecimal.ONE : baseRate;
		BigDecimal db = baseRate == null || baseUsdPerUnit ? BigDecimal.ONE : baseRate;
		BigDecimal nq = quoteRate == null || !quoteUsdPerUnit ? BigDecimal.ONE : quoteRate;
		BigDecimal dq = quoteRate == null || quoteUsdPerUnit ? BigDecimal.ONE : quoteRate;
		BigDecimal exact = nb.multiply(dq).divide(db.multiply(nq), MathContext.DECIMAL128);

		BigDecimal rounded = exact.setScale(Math.max(displayScale, 0), RoundingMode.HALF_UP);
		if (rounded.signum() > 0) {
			return rounded;
		}
		// A tiny positive price must never be shown as zero.
		BigDecimal fallback = exact.setScale(FALLBACK_SCALE, RoundingMode.HALF_UP);
		if (fallback.signum() <= 0) {
			throw new IllegalArgumentException("converted price is not representable");
		}
		return fallback;
	}

	private static void requirePositiveOrAbsent(BigDecimal rate) {
		if (rate != null && rate.signum() <= 0) {
			throw new IllegalArgumentException("price and fx rate must be positive");
		}
	}

}
