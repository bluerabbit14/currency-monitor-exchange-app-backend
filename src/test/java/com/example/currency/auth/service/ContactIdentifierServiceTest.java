package com.example.currency.auth.service;

import com.example.currency.auth.exception.InvalidContactException;
import com.example.currency.user.enums.OtpIdentifierType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactIdentifierServiceTest {

	private final ContactIdentifierService service = new ContactIdentifierService();

	@Test
	void identifiesAndNormalizesEmail() {
		ContactIdentifierService.IdentifiedContact contact = service.identify("  User@Gmail.com  ");

		assertEquals(OtpIdentifierType.EMAIL, contact.type());
		assertEquals("user@gmail.com", contact.value());
	}

	@Test
	void identifiesPhone() {
		ContactIdentifierService.IdentifiedContact contact = service.identify("+919876543210");

		assertEquals(OtpIdentifierType.PHONE, contact.type());
		assertEquals("+919876543210", contact.value());
	}

	@Test
	void rejectsInvalidContact() {
		assertThrows(InvalidContactException.class, () -> service.identify("not-an-email"));
		assertThrows(InvalidContactException.class, () -> service.identify("   "));
		assertThrows(InvalidContactException.class, () -> service.identify(null));
	}

}
