package com.example.currency.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;

public class OptionalHttpUrlValidator implements ConstraintValidator<OptionalHttpUrl, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true;
		}
		try {
			URI uri = new URI(value.trim());
			String scheme = uri.getScheme();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
					&& uri.getHost() != null
					&& !uri.getHost().isBlank();
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

}
