package com.example.currency.systemlog.service;

import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.enums.SystemLogChannel;
import com.example.currency.systemlog.enums.SystemLogStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record SystemLogEvent(
		SystemEventType type,
		SystemLogStatus status,
		SystemLogChannel channel,
		UUID userId,
		String recipient,
		String provider,
		String providerMessageId,
		String referenceType,
		String referenceId,
		String errorCode,
		String errorMessage,
		String ipAddress,
		Map<String, Object> metadata
) {

	public static Builder of(SystemEventType type, SystemLogStatus status) {
		return new Builder(type, status);
	}

	public static final class Builder {

		private final SystemEventType type;
		private final SystemLogStatus status;
		private SystemLogChannel channel;
		private UUID userId;
		private String recipient;
		private String provider;
		private String providerMessageId;
		private String referenceType;
		private String referenceId;
		private String errorCode;
		private String errorMessage;
		private String ipAddress;
		private final Map<String, Object> metadata = new LinkedHashMap<>();

		private Builder(SystemEventType type, SystemLogStatus status) {
			this.type = type;
			this.status = status;
		}

		public Builder channel(SystemLogChannel channel) {
			this.channel = channel;
			return this;
		}

		public Builder userId(UUID userId) {
			this.userId = userId;
			return this;
		}

		public Builder recipient(String recipient) {
			this.recipient = recipient;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public Builder providerMessageId(String providerMessageId) {
			this.providerMessageId = providerMessageId;
			return this;
		}

		public Builder reference(String referenceType, Object referenceId) {
			this.referenceType = referenceType;
			this.referenceId = referenceId == null ? null : referenceId.toString();
			return this;
		}

		public Builder error(String code, String message) {
			this.errorCode = code;
			this.errorMessage = message;
			return this;
		}

		public Builder ipAddress(String ipAddress) {
			this.ipAddress = ipAddress;
			return this;
		}

		public Builder meta(String key, Object value) {
			if (value != null) {
				metadata.put(key, value);
			}
			return this;
		}

		public SystemLogEvent build() {
			return new SystemLogEvent(type, status, channel, userId, recipient, provider, providerMessageId,
					referenceType, referenceId, errorCode, errorMessage, ipAddress,
					metadata.isEmpty() ? null : Map.copyOf(metadata));
		}

	}

}
