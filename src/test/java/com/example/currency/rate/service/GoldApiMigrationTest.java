package com.example.currency.rate.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks on the V19 Gold API mapping migration; it runs against a real database only in the Docker tests.
 */
class GoldApiMigrationTest {

	private final String sql = read("/db/migration/V19__map_commodities_to_goldapi.sql");

	@Test
	void mapsOnlyTheFourConfirmedMetalsAndNotCopper() {
		assertTrue(sql.contains("symbol IN ('XAU', 'XAG', 'XPT', 'XPD')"));
		assertTrue(sql.contains("base.symbol IN ('XAU', 'XAG', 'XPT', 'XPD')"));
		assertFalse(sql.contains("'HG'"), "copper's unit is not documented by the provider, so it stays unmapped");
		assertTrue(sql.contains("provider_code = 'GOLDAPI'"));
		assertTrue(sql.contains("provider_pair_code = base.external_id"));
	}

	@Test
	void onlyFillsEmptyMappingsAndCreatesNothing() {
		assertTrue(sql.contains("provider IS NULL"));
		assertTrue(sql.contains("p.provider_code IS NULL AND p.provider_pair_code IS NULL"));
		assertFalse(sql.contains("INSERT "), "no new assets or pairs: they already exist");
		assertFalse(sql.contains("DROP "));
		assertFalse(sql.contains("DELETE "));
	}

	private static String read(String path) {
		try (InputStream in = GoldApiMigrationTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
