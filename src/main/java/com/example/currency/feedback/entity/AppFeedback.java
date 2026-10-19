package com.example.currency.feedback.entity;

import com.example.currency.common.AuditableEntity;
import com.example.currency.feedback.enums.FeedbackCategory;
import com.example.currency.feedback.enums.FeedbackPlatform;
import com.example.currency.feedback.enums.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "app_feedback")
public class AppFeedback extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Null when the feedback was sent without signing in. */
	@Column(name = "user_id")
	private UUID userId;

	private Short rating;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FeedbackCategory category = FeedbackCategory.GENERAL;

	@Column(columnDefinition = "text")
	private String message;

	@Column(name = "app_version", length = 30)
	private String appVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private FeedbackPlatform platform = FeedbackPlatform.ANDROID;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeedbackStatus status = FeedbackStatus.NEW;

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public FeedbackCategory getCategory() {
		return category;
	}

	public void setCategory(FeedbackCategory category) {
		this.category = category;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

	public FeedbackPlatform getPlatform() {
		return platform;
	}

	public void setPlatform(FeedbackPlatform platform) {
		this.platform = platform;
	}

	public FeedbackStatus getStatus() {
		return status;
	}

	public void setStatus(FeedbackStatus status) {
		this.status = status;
	}

}
