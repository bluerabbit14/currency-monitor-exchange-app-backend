package com.example.currency.rate.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks on the V18 commodity migration; it runs against a real database only in the Docker-based tests.
 */
class CommodityMigrationTest {

	private final String sql = read("/db/migration/V18__seed_commodity_assets.sql");

	@Test
	void seedsAllFiveCommoditiesAsNamedAssets() {
		for (String row : new String[] {"('XAU', 'Gold')", "('XAG', 'Silver')", "('XPT', 'Platinum')",
				"('XPD', 'Palladium')", "('HG', 'Copper')"}) {
			assertTrue(sql.contains(row), row);
		}
		assertTrue(sql.contains("'COMMODITY', NULL"), "commodities carry no currency code");
	}

	@Test
	void renamesTheExistingGoldAndSilverInsteadOfDuplicatingThem() {
		assertTrue(sql.contains("SET symbol = 'XAU'") && sql.contains("symbol = 'GOLD' AND asset_type = 'COMMODITY'"));
		assertTrue(sql.contains("SET symbol = 'XAG'") && sql.contains("symbol = 'SILVER' AND asset_type = 'COMMODITY'"));
		assertTrue(sql.contains("SET symbol = 'XAU/USD'") && sql.contains("SET symbol = 'XAG/USD'"));
		assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol)"));
	}

	@Test
	void createsUnmappedUsdPairsOnlyWhereMissingAndIsNotDestructive() {
		assertTrue(sql.contains("'ACTIVE', NULL, NULL"), "no provider is mapped for commodities yet");
		assertTrue(sql.contains("p.base_asset_id = base.id AND p.quote_asset_id = usd.id"));
		assertFalse(sql.contains("DROP "));
		assertFalse(sql.contains("DELETE "));
	}

	private static String read(String path) {
		try (InputStream in = CommodityMigrationTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
