package com.example.currency.user.entity;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.common.AuditableEntity;
import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(length = 320)
	private String email;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "display_name", length = 100)
	private String displayName;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "profile_image_url")
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "base_asset_id")
	private UUID baseAssetId;

	@Column(length = 16)
	private String locale;

	@Column(length = 64)
	private String timezone;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "deletion_requested_at")
	private Instant deletionRequestedAt;

	@Column(name = "deletion_scheduled_at")
	private Instant deletionScheduledAt;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<UserAuthProvider> authProviders = new LinkedHashSet<>();

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<UserDevice> devices = new LinkedHashSet<>();

	@OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
	private NotificationPreference notificationPreference;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<PriceAlert> priceAlerts = new LinkedHashSet<>();

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<Notification> notifications = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public void setProfileImageUrl(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public UUID getBaseAssetId() {
		return baseAssetId;
	}

	public void setBaseAssetId(UUID baseAssetId) {
		this.baseAssetId = baseAssetId;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public void setEmailVerifiedAt(Instant emailVerifiedAt) {
		this.emailVerifiedAt = emailVerifiedAt;
	}

	public Instant getDeletionRequestedAt() {
		return deletionRequestedAt;
	}

	public Instant getDeletionScheduledAt() {
		return deletionScheduledAt;
	}

	public void scheduleDeletion(Instant requestedAt, Instant scheduledAt) {
		this.status = UserStatus.PENDING_DELETION;
		this.deletionRequestedAt = requestedAt;
		this.deletionScheduledAt = scheduledAt;
	}

	public void cancelDeletion() {
		this.status = UserStatus.ACTIVE;
		this.deletionRequestedAt = null;
		this.deletionScheduledAt = null;
	}

	public Set<UserAuthProvider> getAuthProviders() {
		return authProviders;
	}

	public Set<UserDevice> getDevices() {
		return devices;
	}

	public NotificationPreference getNotificationPreference() {
		return notificationPreference;
	}

	public Set<PriceAlert> getPriceAlerts() {
		return priceAlerts;
	}

	public Set<Notification> getNotifications() {
		return notifications;
	}

}
