package com.example.currency.auth.service;

import com.example.currency.auth.config.AdminProperties;
import com.example.currency.auth.exception.AdminAccessDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards admin endpoints with a shared API key sent in the {@code X-Admin-Key} header. With no key configured
 * every admin request is refused, so a missing setting never opens the endpoints.
 */
@Service
public class AdminKeyService {

	public static final String HEADER = "X-Admin-Key";

	private final byte[] expected;

	public AdminKeyService(AdminProperties properties) {
		String key = properties.getApiKey();
		this.expected = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
	}

	public void requireAdmin(String providedKey) {
		if (expected == null || providedKey == null) {
			throw new AdminAccessDeniedException();
		}
		// Constant-time comparison.
		if (!MessageDigest.isEqual(expected, providedKey.getBytes(StandardCharsets.UTF_8))) {
			throw new AdminAccessDeniedException();
		}
	}

}
