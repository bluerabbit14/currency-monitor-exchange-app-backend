package com.example.currency.rate.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Converts a USD price into another fiat currency using a stored FX rate. Pure BigDecimal arithmetic.
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
		if (usdPrice == null || usdPrice.signum() <= 0 || fxRate == null || fxRate.signum() <= 0) {
			throw new IllegalArgumentException("price and fx rate must be positive");
		}
		BigDecimal exact = fxIsQuotePerUsd
				? usdPrice.multiply(fxRate)
				: usdPrice.divide(fxRate, MathContext.DECIMAL128);
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

}
