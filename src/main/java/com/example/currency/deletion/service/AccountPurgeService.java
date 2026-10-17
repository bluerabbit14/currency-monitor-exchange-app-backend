package com.example.currency.deletion.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Permanently erases accounts whose deletion grace period has ended.
 * Payment data (subscriptions and their payment events) is never deleted: when an account has any,
 * the users row stays as an anonymized tombstone so those records keep a valid owner.
 */
@Service
public class AccountPurgeService {

	private final JdbcTemplate jdbc;

	public AccountPurgeService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<UUID> findDue(Instant now, int limit) {
		return jdbc.queryForList("""
				SELECT id FROM users
				WHERE status = 'PENDING_DELETION' AND deletion_scheduled_at <= ?
				ORDER BY deletion_scheduled_at
				LIMIT ?
				""", UUID.class, Timestamp.from(now), limit);
	}

	/**
	 * Erases one account. Empty when it is no longer due (restored by a login, or already purged);
	 * otherwise whether the anonymized row was kept for payment records.
	 */
	@Transactional
	public Optional<Boolean> purge(UUID userId, Instant now) {
		// Row lock: a concurrent login that cancels the deletion either commits first (we skip) or waits for us.
		List<UUID> locked = jdbc.queryForList("""
				SELECT id FROM users
				WHERE id = ? AND status = 'PENDING_DELETION' AND deletion_scheduled_at <= ?
				FOR UPDATE SKIP LOCKED
				""", UUID.class, userId, Timestamp.from(now));
		if (locked.isEmpty()) {
			return Optional.empty();
		}

		// Personal data. alert_events go with their alerts (cascade).
		jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM price_alerts WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM user_favorites WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM user_devices WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM notification_preferences WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM user_auth_providers WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM refresh_sessions WHERE user_id = ?", userId);
		// The audit trail stays but no longer identifies the person.
		jdbc.update("UPDATE system_logs SET recipient = NULL, ip_address = NULL WHERE user_id = ?", userId);

		Integer subscriptions = jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscriptions WHERE user_id = ?", Integer.class, userId);
		boolean keptForPayments = subscriptions != null && subscriptions > 0;
		if (keptForPayments) {
			jdbc.update("""
					UPDATE users
					SET email = NULL, phone_number = NULL, display_name = NULL, profile_image_url = NULL,
					    locale = NULL, timezone = NULL, base_asset_id = NULL, email_verified_at = NULL,
					    last_login_at = NULL, deletion_scheduled_at = NULL, status = 'DELETED',
					    updated_at = ?
					WHERE id = ?
					""", Timestamp.from(now), userId);
		}
		else {
			jdbc.update("DELETE FROM users WHERE id = ?", userId);
		}
		return Optional.of(keptForPayments);
	}

}
