package com.example.currency.auth.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks on the SQL that backs Google identities. The behaviour against a real database is covered by
 * the Docker-based {@code UserAuthProviderGoogleIT}.
 */
class GoogleIdentityMigrationTest {

	@Test
	void v21AddsAPartialUniqueIndexAndNothingDestructive() {
		String sql = read("/db/migration/V21__google_identity_one_per_user.sql");

		assertTrue(sql.contains("CREATE UNIQUE INDEX user_auth_providers_user_google_uq"));
		assertTrue(sql.contains("ON user_auth_providers (user_id)"));
		assertTrue(sql.contains("WHERE provider = 'GOOGLE'"), "only Google is limited to one per user");
		assertFalse(sql.contains("DROP "));
		assertFalse(sql.contains("DELETE "));
		assertFalse(sql.contains("ALTER TABLE"));
	}

	@Test
	void theBaseSchemaAlreadyEnforcesOneUserPerProviderSubjectAndAllowsGoogle() {
		String base = read("/db/migration/V1__init_domain.sql");

		assertTrue(base.contains("CONSTRAINT user_auth_providers_provider_user_uq UNIQUE (provider, provider_user_id)"));
		assertTrue(base.contains("provider IN ('GOOGLE', 'APPLE', 'EMAIL', 'PHONE')"));
	}

	private static String read(String path) {
		try (InputStream in = GoogleIdentityMigrationTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
