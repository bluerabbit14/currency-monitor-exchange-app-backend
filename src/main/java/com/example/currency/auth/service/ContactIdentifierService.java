package com.example.currency.auth.service;

import com.example.currency.auth.exception.InvalidContactException;
import com.example.currency.user.enums.OtpIdentifierType;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ContactIdentifierService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

	private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

	public IdentifiedContact identify(String contact) {
		if (contact == null || contact.isBlank()) {
			throw new InvalidContactException();
		}
		String trimmed = contact.trim();
		if (EMAIL_PATTERN.matcher(trimmed).matches()) {
			return new IdentifiedContact(OtpIdentifierType.EMAIL, trimmed.toLowerCase(Locale.ROOT));
		}
		if (PHONE_PATTERN.matcher(trimmed).matches()) {
			return new IdentifiedContact(OtpIdentifierType.PHONE, trimmed);
		}
		throw new InvalidContactException();
	}

	public record IdentifiedContact(OtpIdentifierType type, String value) {
	}

}
