package com.example.currency.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

	private int ttlSeconds = 300;

	private int maxAttempts = 5;

	private String pepper;

	public int getTtlSeconds() {
		return ttlSeconds;
	}

	public void setTtlSeconds(int ttlSeconds) {
		this.ttlSeconds = ttlSeconds;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public String getPepper() {
		return pepper;
	}

	public void setPepper(String pepper) {
		this.pepper = pepper;
	}

}
