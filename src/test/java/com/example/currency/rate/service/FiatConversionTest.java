package com.example.currency.rate.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiatConversionTest {

	@Test
	void multipliesByAUsdToFiatRate() {
		assertEquals(new BigDecimal("9000000.00"),
				FiatConversion.usdToFiat(new BigDecimal("100000"), new BigDecimal("90"), true, 2));
	}

	@Test
	void dividesByAFiatToUsdRate() {
		// EUR/USD = 1.25 means 1 EUR = 1.25 USD, so 100000 USD = 80000 EUR.
		assertEquals(new BigDecimal("80000.00"),
				FiatConversion.usdToFiat(new BigDecimal("100000"), new BigDecimal("1.25"), false, 2));
	}

	@Test
	void roundsHalfUpToTheCurrencyScale() {
		assertEquals(new BigDecimal("0.67"),
				FiatConversion.usdToFiat(new BigDecimal("1"), new BigDecimal("0.665"), true, 2));
		assertEquals(new BigDecimal("0.33"),
				FiatConversion.usdToFiat(new BigDecimal("1"), new BigDecimal("3"), false, 2));
		assertEquals(new BigDecimal("15000"),
				FiatConversion.usdToFiat(new BigDecimal("100"), new BigDecimal("150.0049"), true, 0));
	}

	@Test
	void keepsStablecoinMarketPricesInsteadOfForcingOne() {
		assertEquals(new BigDecimal("0.9993"),
				FiatConversion.usdToFiat(new BigDecimal("0.9993"), BigDecimal.ONE, true, 4));
		assertEquals(new BigDecimal("89.94"),
				FiatConversion.usdToFiat(new BigDecimal("0.9993"), new BigDecimal("90"), true, 2));
	}

	@Test
	void neverRoundsATinyPositivePriceToZero() {
		BigDecimal result = FiatConversion.usdToFiat(new BigDecimal("0.000001"), new BigDecimal("0.5"), true, 2);

		assertEquals(0, new BigDecimal("0.0000005").compareTo(result));
	}

	@Test
	void crossRatesDivideTheUsdValuesOfBothSides() {
		BigDecimal eurUsd = new BigDecimal("1.25");
		BigDecimal usdInr = new BigDecimal("90");
		BigDecimal usdJpy = new BigDecimal("150");
		// EUR (USD per unit) -> INR (units per USD)
		assertEquals(new BigDecimal("112.50"), FiatConversion.cross(eurUsd, true, usdInr, false, 2));
		// INR -> JPY, both units per USD: 150 / 90 = 1.6667
		assertEquals(new BigDecimal("1.67"), FiatConversion.cross(usdInr, false, usdJpy, false, 2));
		// Base is USD (no leg), quote INR / base INR, quote USD (no leg)
		assertEquals(new BigDecimal("90.00"), FiatConversion.cross(null, true, usdInr, false, 2));
		assertEquals(new BigDecimal("0.0111"), FiatConversion.cross(usdInr, false, null, true, 4));
		// Round-trip stays consistent: EUR->INR times INR->EUR is 1.
		BigDecimal there = FiatConversion.cross(eurUsd, true, usdInr, false, 12);
		BigDecimal back = FiatConversion.cross(usdInr, false, eurUsd, true, 12);
		assertEquals(0, new BigDecimal("1").compareTo(there.multiply(back).setScale(6, java.math.RoundingMode.HALF_UP)));
	}

	@Test
	void crossRejectsNonPositiveLegs() {
		BigDecimal ten = BigDecimal.TEN;
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.cross(ten, true, BigDecimal.ZERO, false, 2));
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.cross(new BigDecimal("-1"), true, ten, false, 2));
	}

	@Test
	void rejectsMissingZeroAndNegativeInputsInsteadOfReturningZero() {
		BigDecimal ten = BigDecimal.TEN;
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.usdToFiat(ten, BigDecimal.ZERO, false, 2));
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.usdToFiat(ten, new BigDecimal("-1"), true, 2));
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.usdToFiat(BigDecimal.ZERO, ten, true, 2));
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.usdToFiat(null, ten, true, 2));
		assertThrows(IllegalArgumentException.class, () -> FiatConversion.usdToFiat(ten, null, true, 2));
	}

}
