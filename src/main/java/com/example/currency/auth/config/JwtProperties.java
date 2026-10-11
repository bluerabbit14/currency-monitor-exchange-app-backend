package com.example.currency.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	private String secret;

	private long accessTtlSeconds = 900;

	private long refreshTtlSeconds = 2_592_000;

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getAccessTtlSeconds() {
		return accessTtlSeconds;
	}

	public void setAccessTtlSeconds(long accessTtlSeconds) {
		this.accessTtlSeconds = accessTtlSeconds;
	}

	public long getRefreshTtlSeconds() {
		return refreshTtlSeconds;
	}

	public void setRefreshTtlSeconds(long refreshTtlSeconds) {
		this.refreshTtlSeconds = refreshTtlSeconds;
	}

}
