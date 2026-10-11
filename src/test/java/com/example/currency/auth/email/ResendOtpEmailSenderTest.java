package com.example.currency.auth.email;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.config.ResendProperties;
import com.example.currency.auth.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class ResendOtpEmailSenderTest {

	@Test
	void sendsPublishedTemplateWithoutFromWhenUnset() {
		ResendProperties properties = resendProperties("");
		OtpProperties otpProperties = otpProperties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ResendOtpEmailSender sender = new ResendOtpEmailSender(builder, properties, otpProperties);
		server.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(method(POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key"))
				.andExpect(content().json("""
						{
						  "to": ["user@gmail.com"],
						  "template": {
						    "id": "send-otp",
						    "variables": {
						      "otp": "123456",
						      "expiryTime": "5 minutes"
						    }
						  }
						}
						""", true))
				.andRespond(withSuccess("{\"id\":\"email_1\"}", MediaType.APPLICATION_JSON));

		sender.sendOtp("user@gmail.com", "123456");

		server.verify();
	}

	@Test
	void includesFromWhenConfigured() {
		ResendProperties properties = resendProperties("Currency <otp@example.com>");
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ResendOtpEmailSender sender = new ResendOtpEmailSender(builder, properties, otpProperties());
		server.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(content().json("""
						{
						  "from": "Currency <otp@example.com>",
						  "to": ["user@gmail.com"],
						  "template": {
						    "id": "send-otp",
						    "variables": {
						      "otp": "654321",
						      "expiryTime": "5 minutes"
						    }
						  }
						}
						""", true))
				.andRespond(withSuccess("{\"id\":\"email_2\"}", MediaType.APPLICATION_JSON));

		sender.sendOtp("user@gmail.com", "654321");

		server.verify();
	}

	@Test
	void deliveryFailureDoesNotExposeTheProviderResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ResendOtpEmailSender sender = new ResendOtpEmailSender(builder, resendProperties(""), otpProperties());
		server.expect(requestTo("https://api.resend.com/emails"))
				.andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("otp=123456"));

		EmailDeliveryException exception = assertThrows(
				EmailDeliveryException.class,
				() -> sender.sendOtp("user@gmail.com", "123456"));

		assertEquals("Unable to send OTP", exception.getMessage());
		assertFalse(exception.getMessage().contains("123456"));
		server.verify();
	}

	private static ResendProperties resendProperties(String from) {
		ResendProperties properties = new ResendProperties();
		properties.setApiKey("re_test_key");
		properties.setBaseUrl("https://api.resend.com");
		properties.setFrom(from);
		return properties;
	}

	private static OtpProperties otpProperties() {
		OtpProperties properties = new OtpProperties();
		properties.setTtlSeconds(300);
		properties.setPepper("test-otp-pepper");
		return properties;
	}

}
