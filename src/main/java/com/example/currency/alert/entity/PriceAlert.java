package com.example.currency.alert.entity;

import com.example.currency.alert.enums.AlertCondition;
import com.example.currency.alert.enums.AlertStatus;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.common.AuditableEntity;
import com.example.currency.notification.entity.Notification;
import com.example.currency.user.entity.User;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
		name = "price_alerts",
		indexes = {
				@Index(name = "price_alerts_user_status_idx", columnList = "user_id, status"),
				@Index(name = "price_alerts_user_created_at_idx", columnList = "user_id, created_at")
		}
)
public class PriceAlert extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pair_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private AssetPair pair;

	@Enumerated(EnumType.STRING)
	@Column(name = "condition", nullable = false, length = 32)
	private AlertCondition condition;

	@Column(name = "target_price", nullable = false, precision = 28, scale = 12)
	private BigDecimal targetPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AlertStatus status = AlertStatus.ACTIVE;

	@Column(name = "triggered_at")
	private Instant triggeredAt;

	@OneToMany(mappedBy = "alert", fetch = FetchType.LAZY)
	private Set<AlertEvent> events = new LinkedHashSet<>();

	@OneToMany(mappedBy = "alert", fetch = FetchType.LAZY)
	private Set<Notification> notifications = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public AssetPair getPair() {
		return pair;
	}

	public void setPair(AssetPair pair) {
		this.pair = pair;
	}

	public AlertCondition getCondition() {
		return condition;
	}

	public void setCondition(AlertCondition condition) {
		this.condition = condition;
	}

	public BigDecimal getTargetPrice() {
		return targetPrice;
	}

	public void setTargetPrice(BigDecimal targetPrice) {
		this.targetPrice = targetPrice;
	}

	public AlertStatus getStatus() {
		return status;
	}

	public void setStatus(AlertStatus status) {
		this.status = status;
	}

	public Instant getTriggeredAt() {
		return triggeredAt;
	}

	public void setTriggeredAt(Instant triggeredAt) {
		this.triggeredAt = triggeredAt;
	}

	public Set<AlertEvent> getEvents() {
		return events;
	}

	public Set<Notification> getNotifications() {
		return notifications;
	}

}
