package com.example.currency.user.entity;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.common.AuditableEntity;
import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.watchlist.entity.Watchlist;
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

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<UserAuthProvider> authProviders = new LinkedHashSet<>();

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<OtpVerification> otpVerifications = new LinkedHashSet<>();

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<UserDevice> devices = new LinkedHashSet<>();

	@OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
	private NotificationPreference notificationPreference;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private Set<Watchlist> watchlists = new LinkedHashSet<>();

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

	public Set<UserAuthProvider> getAuthProviders() {
		return authProviders;
	}

	public Set<OtpVerification> getOtpVerifications() {
		return otpVerifications;
	}

	public Set<UserDevice> getDevices() {
		return devices;
	}

	public NotificationPreference getNotificationPreference() {
		return notificationPreference;
	}

	public Set<Watchlist> getWatchlists() {
		return watchlists;
	}

	public Set<PriceAlert> getPriceAlerts() {
		return priceAlerts;
	}

	public Set<Notification> getNotifications() {
		return notifications;
	}

}
