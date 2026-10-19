package com.example.currency.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin endpoints are closed unless an API key is configured ({@code ADMIN_API_KEY}). */
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

	private String apiKey = "";

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

}
