package com.example.currency.alert.entity;

import com.example.currency.notification.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
public class AlertEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alert_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private PriceAlert alert;

	@Column(name = "triggered_price", nullable = false, precision = 28, scale = 12)
	private BigDecimal triggeredPrice;

	@Column(name = "triggered_at", nullable = false)
	private Instant triggeredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_status", nullable = false, length = 32)
	private DeliveryStatus notificationStatus = DeliveryStatus.PENDING;

	public UUID getId() {
		return id;
	}

	public PriceAlert getAlert() {
		return alert;
	}

	public void setAlert(PriceAlert alert) {
		this.alert = alert;
	}

	public BigDecimal getTriggeredPrice() {
		return triggeredPrice;
	}

	public void setTriggeredPrice(BigDecimal triggeredPrice) {
		this.triggeredPrice = triggeredPrice;
	}

	public Instant getTriggeredAt() {
		return triggeredAt;
	}

	public void setTriggeredAt(Instant triggeredAt) {
		this.triggeredAt = triggeredAt;
	}

	public DeliveryStatus getNotificationStatus() {
		return notificationStatus;
	}

	public void setNotificationStatus(DeliveryStatus notificationStatus) {
		this.notificationStatus = notificationStatus;
	}

}
