package com.example.currency.user.entity;

import com.example.currency.common.CreatedAtEntity;
import com.example.currency.user.enums.OtpIdentifierType;
import com.example.currency.user.enums.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "otp_verifications",
		indexes = @Index(name = "otp_verifications_user_id_idx", columnList = "user_id")
)
public class OtpVerification extends CreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(nullable = false, length = 320)
	private String identifier;

	@Enumerated(EnumType.STRING)
	@Column(name = "identifier_type", nullable = false, length = 32)
	private OtpIdentifierType identifierType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OtpPurpose purpose;

	@Column(name = "otp_hash", nullable = false, length = 255)
	private String otpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public OtpIdentifierType getIdentifierType() {
		return identifierType;
	}

	public void setIdentifierType(OtpIdentifierType identifierType) {
		this.identifierType = identifierType;
	}

	public OtpPurpose getPurpose() {
		return purpose;
	}

	public void setPurpose(OtpPurpose purpose) {
		this.purpose = purpose;
	}

	public String getOtpHash() {
		return otpHash;
	}

	public void setOtpHash(String otpHash) {
		this.otpHash = otpHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public void setVerifiedAt(Instant verifiedAt) {
		this.verifiedAt = verifiedAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

}
