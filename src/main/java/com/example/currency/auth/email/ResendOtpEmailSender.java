package com.example.currency.auth.email;

import com.example.currency.auth.config.OtpProperties;
import com.example.currency.auth.config.ResendProperties;
import com.example.currency.auth.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResendOtpEmailSender implements OtpEmailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendOtpEmailSender.class);

	private final RestClient restClient;
	private final ResendProperties resendProperties;
	private final OtpProperties otpProperties;

	public ResendOtpEmailSender(
			RestClient.Builder restClientBuilder,
			ResendProperties resendProperties,
			OtpProperties otpProperties) {
		if (resendProperties.getApiKey() == null || resendProperties.getApiKey().isBlank()) {
			throw new IllegalStateException("Resend API key is not configured");
		}
		this.resendProperties = resendProperties;
		this.otpProperties = otpProperties;
		this.restClient = restClientBuilder
				.baseUrl(resendProperties.getBaseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendProperties.getApiKey())
				.build();
	}

	@Override
	public void sendOtp(String email, String otp) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", resendProperties.getFrom());
		body.put("to", List.of(email));
		body.put("template", Map.of(
				"id", ResendEmailConstants.OTP_TEMPLATE_ID,
				"variables", Map.of(
						"otp", otp,
						"expiryTime", expiryLabel())));
		try {
			restClient.post()
					.uri("/emails")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			log.warn("Resend OTP delivery failed with status {}: {}", ex.getStatusCode().value(),
					ex.getResponseBodyAsString());
			throw new EmailDeliveryException();
		}
		catch (RestClientException ex) {
			log.warn("Resend OTP delivery failed: {}", ex.getMessage());
			throw new EmailDeliveryException();
		}
	}

	private String expiryLabel() {
		long minutes = Math.max(1, otpProperties.getTtlSeconds() / 60L);
		return minutes == 1 ? "1 minute" : minutes + " minutes";
	}

}
