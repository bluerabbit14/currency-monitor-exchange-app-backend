package com.example.currency.rate.service;

import com.example.currency.rate.config.RateProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks on the V17 fiat catalog migration. It runs against a real database only in the Docker-based
 * integration tests, so these guard the parts that are easy to get wrong.
 */
class FiatCatalogMigrationTest {

	private final String sql = read("/db/migration/V17__seed_supported_fiat_usd_pairs.sql");

	@Test
	void addsOnlyTheMissingFiatAssetsAndReusesTheRest() {
		assertTrue(sql.contains("('CNY', 'Chinese Yuan')"));
		assertTrue(sql.contains("('AED', 'UAE Dirham')"));
		for (String alreadySeeded : new String[] {"'USD', 'US Dollar'", "'EUR', 'Euro'", "'GBP', 'British Pound'",
				"'INR', 'Indian Rupee'", "'JPY', 'Japanese Yen'"}) {
			assertFalse(sql.contains(alreadySeeded), alreadySeeded + " already exists and must not be re-inserted");
		}
		assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol)"));
		assertTrue(sql.contains("'FIAT', v.symbol"), "fiat assets need currency_code set");
	}

	@Test
	void createsOnlyTheThreeMissingUsdPairsInEitherDirectionOnce() {
		assertTrue(sql.contains("quote.symbol IN ('JPY', 'CNY', 'AED')"));
		assertTrue(sql.contains("p.base_asset_id = usd.id AND p.quote_asset_id = quote.id"));
		assertTrue(sql.contains("p.base_asset_id = quote.id AND p.quote_asset_id = usd.id"),
				"a pair stored in the opposite direction (EUR/USD) must also count as existing");
		assertTrue(sql.contains("'TWELVEDATA'"));
		assertFalse(sql.contains("DROP "), "no destructive statements");
		assertFalse(sql.contains("DELETE "), "no destructive statements");
	}

	@Test
	void sixUsdPairsFitTheFreePlanAtTheDefaultInterval() {
		Duration interval = new RateProperties().getCollection().getInterval();
		long runsPerDay = Duration.ofDays(1).dividedBy(interval);
		int pairs = 6; // USD/INR, EUR/USD, GBP/USD, USD/JPY, USD/CNY, USD/AED

		assertEquals(Duration.ofMinutes(12), interval);
		assertTrue(pairs * runsPerDay <= new RateProperties().getTwelvedata().getDailyRequestLimit(),
				"credits per day: " + pairs * runsPerDay);
		assertTrue(interval.compareTo(new RateProperties().getStaleAfter()) < 0,
				"fiat rates must not be stale between two successful runs");
	}

	private static String read(String path) {
		try (InputStream in = FiatCatalogMigrationTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
