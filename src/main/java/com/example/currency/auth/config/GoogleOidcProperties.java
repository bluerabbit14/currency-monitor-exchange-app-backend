package com.example.currency.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oidc.google")
public class GoogleOidcProperties {

	/**
	 * The Google OAuth Web client ID that ID tokens must be issued for (the {@code aud} claim).
	 * Read from GOOGLE_WEB_CLIENT_ID. It is public, but Google sign-in stays off until it is set.
	 */
	private String clientId;

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank();
	}

}
