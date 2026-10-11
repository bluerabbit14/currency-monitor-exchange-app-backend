package com.example.currency.auth.service;

import com.example.currency.auth.config.OtpProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class OtpHasher {

	private static final String HMAC_SHA256 = "HmacSHA256";

	private final byte[] pepper;

	public OtpHasher(OtpProperties properties) {
		String configured = properties.getPepper();
		if (configured == null || configured.isBlank()) {
			throw new IllegalStateException("OTP pepper is not configured");
		}
		this.pepper = configured.getBytes(StandardCharsets.UTF_8);
	}

	public String hash(String otp) {
		return HexFormat.of().formatHex(hmac(otp));
	}

	public boolean matches(String otp, String expectedHash) {
		if (expectedHash == null || expectedHash.isBlank()) {
			return false;
		}
		byte[] actual = hmac(otp);
		byte[] expected;
		try {
			expected = HexFormat.of().parseHex(expectedHash);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		return MessageDigest.isEqual(actual, expected);
	}

	private byte[] hmac(String otp) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(pepper, HMAC_SHA256));
			return mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to hash OTP", ex);
		}
	}

}
