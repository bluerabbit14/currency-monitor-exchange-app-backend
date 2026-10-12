package com.example.currency.notification.entity;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.common.CreatedAtEntity;
import com.example.currency.notification.enums.DeliveryStatus;
import com.example.currency.notification.enums.NotificationType;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "notifications",
		indexes = @Index(name = "notifications_alert_id_idx", columnList = "alert_id")
)
public class Notification extends CreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alert_id")
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private PriceAlert alert;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NotificationType type;

	@Column(nullable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(nullable = false)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DeliveryStatus status = DeliveryStatus.PENDING;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@Column(name = "read_at")
	private Instant readAt;

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public PriceAlert getAlert() {
		return alert;
	}

	public void setAlert(PriceAlert alert) {
		this.alert = alert;
	}

	public NotificationType getType() {
		return type;
	}

	public void setType(NotificationType type) {
		this.type = type;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public void setStatus(DeliveryStatus status) {
		this.status = status;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public void setSentAt(Instant sentAt) {
		this.sentAt = sentAt;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public void setReadAt(Instant readAt) {
		this.readAt = readAt;
	}

}
