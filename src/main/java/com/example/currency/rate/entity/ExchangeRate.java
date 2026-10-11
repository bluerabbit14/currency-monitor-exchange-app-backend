package com.example.currency.rate.entity;

import com.example.currency.asset.entity.AssetPair;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

	@Id
	@Column(name = "pair_id")
	private UUID pairId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "pair_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private AssetPair pair;

	@Column(nullable = false, precision = 28, scale = 12)
	private BigDecimal rate;

	@Column(name = "previous_rate", precision = 28, scale = 12)
	private BigDecimal previousRate;

	@Column(name = "change_percent", precision = 28, scale = 12)
	private BigDecimal changePercent;

	@Column(nullable = false, length = 64)
	private String source;

	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	@PreUpdate
	protected void onWrite() {
		updatedAt = Instant.now();
	}

	public UUID getPairId() {
		return pairId;
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

	public BigDecimal getPreviousRate() {
		return previousRate;
	}

	public void setPreviousRate(BigDecimal previousRate) {
		this.previousRate = previousRate;
	}

	public BigDecimal getChangePercent() {
		return changePercent;
	}

	public void setChangePercent(BigDecimal changePercent) {
		this.changePercent = changePercent;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public void setFetchedAt(Instant fetchedAt) {
		this.fetchedAt = fetchedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
