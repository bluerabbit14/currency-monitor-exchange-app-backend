package com.example.currency.systemlog.enums;

import static com.example.currency.systemlog.enums.SystemEventCategory.AUTH;
import static com.example.currency.systemlog.enums.SystemEventCategory.NOTIFICATION;
import static com.example.currency.systemlog.enums.SystemEventCategory.USER;

/**
 * Event names are stored as plain strings (no DB check constraint) so a new event
 * needs only a new constant here, not a migration.
 */
public enum SystemEventType {
	OTP_SENT(AUTH),
	OTP_SEND_SKIPPED(AUTH),
	OTP_SEND_FAILED(AUTH),
	OTP_VERIFIED(AUTH),
	OTP_VERIFY_FAILED(AUTH),
	OTP_ATTEMPTS_EXCEEDED(AUTH),
	LOGIN_SUCCESS(AUTH),
	REGISTRATION_COMPLETED(USER),
	ACCOUNT_DELETION_REQUESTED(USER),
	ACCOUNT_DELETION_CANCELLED(USER),
	ACCOUNT_PURGED(USER),
	ACCOUNT_PURGE_FAILED(USER),
	NOTIFICATION_DELIVERY(NOTIFICATION);

	private final SystemEventCategory category;

	SystemEventType(SystemEventCategory category) {
		this.category = category;
	}

	public SystemEventCategory category() {
		return category;
	}

}
