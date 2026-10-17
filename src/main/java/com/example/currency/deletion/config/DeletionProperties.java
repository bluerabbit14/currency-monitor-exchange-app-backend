package com.example.currency.deletion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deletion")
public class DeletionProperties {

	private int gracePeriodDays = 15;

	private long pollIntervalMs = 60000;

	private int batchSize = 20;

	public int getGracePeriodDays() {
		return gracePeriodDays;
	}

	public void setGracePeriodDays(int gracePeriodDays) {
		this.gracePeriodDays = gracePeriodDays;
	}

	public long getPollIntervalMs() {
		return pollIntervalMs;
	}

	public void setPollIntervalMs(long pollIntervalMs) {
		this.pollIntervalMs = pollIntervalMs;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

}
