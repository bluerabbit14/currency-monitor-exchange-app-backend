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

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	@Column(name = "provider_code", nullable = false, length = 64)
	private String providerCode;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	@PreUpdate
	void touchUpdatedAt() {
		updatedAt = Instant.now();
	}

	public Instant getUpdatedAt() {
		return updatedAt;
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

	public Instant getObservedAt() {
		return observedAt;
	}

	public void setObservedAt(Instant observedAt) {
		this.observedAt = observedAt;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public void setFetchedAt(Instant fetchedAt) {
		this.fetchedAt = fetchedAt;
	}

	public String getProviderCode() {
		return providerCode;
	}

	public void setProviderCode(String providerCode) {
		this.providerCode = providerCode;
	}

}
