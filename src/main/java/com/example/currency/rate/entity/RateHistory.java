package com.example.currency.rate.entity;

import com.example.currency.asset.entity.AssetPair;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "rate_history",
		uniqueConstraints = @UniqueConstraint(
				name = "rate_history_pair_recorded_uq",
				columnNames = {"pair_id", "recorded_at"}
		)
)
public class RateHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pair_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private AssetPair pair;

	@Column(nullable = false, precision = 28, scale = 12)
	private BigDecimal rate;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(nullable = false, length = 64)
	private String source;

	public UUID getId() {
		return id;
	}

	public AssetPair getPair() {
		return pair;
	}

	public void setPair(AssetPair pair) {
		this.pair = pair;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public void setRate(BigDecimal rate) {
		this.rate = rate;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(Instant recordedAt) {
		this.recordedAt = recordedAt;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

}
