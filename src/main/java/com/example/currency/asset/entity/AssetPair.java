package com.example.currency.asset.entity;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.common.AuditableEntity;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
		name = "asset_pairs",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "asset_pairs_base_quote_uq",
						columnNames = {"base_asset_id", "quote_asset_id"}
				),
				@UniqueConstraint(name = "asset_pairs_symbol_uq", columnNames = "symbol")
		},
		indexes = @Index(name = "asset_pairs_quote_asset_id_idx", columnList = "quote_asset_id")
)
public class AssetPair extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "base_asset_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private Asset baseAsset;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quote_asset_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private Asset quoteAsset;

	@Column(nullable = false, length = 32)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AssetStatus status = AssetStatus.ACTIVE;

	@Column(name = "provider_code", length = 64)
	private String providerCode;

	@Column(name = "provider_pair_code", length = 64)
	private String providerPairCode;

	@OneToOne(mappedBy = "pair", fetch = FetchType.LAZY)
	private ExchangeRate exchangeRate;

	@OneToMany(mappedBy = "pair", fetch = FetchType.LAZY)
	private Set<RateHistory> rateHistory = new LinkedHashSet<>();

	@OneToMany(mappedBy = "pair", fetch = FetchType.LAZY)
	private Set<PriceAlert> priceAlerts = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public Asset getBaseAsset() {
		return baseAsset;
	}

	public void setBaseAsset(Asset baseAsset) {
		this.baseAsset = baseAsset;
	}

	public Asset getQuoteAsset() {
		return quoteAsset;
	}

	public void setQuoteAsset(Asset quoteAsset) {
		this.quoteAsset = quoteAsset;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public AssetStatus getStatus() {
		return status;
	}

	public void setStatus(AssetStatus status) {
		this.status = status;
	}

	public String getProviderCode() {
		return providerCode;
	}

	public void setProviderCode(String providerCode) {
		this.providerCode = providerCode;
	}

	public String getProviderPairCode() {
		return providerPairCode;
	}

	public void setProviderPairCode(String providerPairCode) {
		this.providerPairCode = providerPairCode;
	}

	public ExchangeRate getExchangeRate() {
		return exchangeRate;
	}

	public Set<RateHistory> getRateHistory() {
		return rateHistory;
	}

	public Set<PriceAlert> getPriceAlerts() {
		return priceAlerts;
	}

}
