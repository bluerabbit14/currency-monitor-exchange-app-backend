package com.example.currency.notification.entity;

import com.example.currency.common.AuditableEntity;
import com.example.currency.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends AuditableEntity {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(name = "push_enabled", nullable = false)
	private boolean pushEnabled = true;

	@Column(name = "in_app_enabled", nullable = false)
	private boolean inAppEnabled = true;

	@Column(name = "email_enabled", nullable = false)
	private boolean emailEnabled = false;

	public UUID getUserId() {
		return userId;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public boolean isPushEnabled() {
		return pushEnabled;
	}

	public void setPushEnabled(boolean pushEnabled) {
		this.pushEnabled = pushEnabled;
	}

	public boolean isInAppEnabled() {
		return inAppEnabled;
	}

	public void setInAppEnabled(boolean inAppEnabled) {
		this.inAppEnabled = inAppEnabled;
	}

	public boolean isEmailEnabled() {
		return emailEnabled;
	}

	public void setEmailEnabled(boolean emailEnabled) {
		this.emailEnabled = emailEnabled;
	}

}
