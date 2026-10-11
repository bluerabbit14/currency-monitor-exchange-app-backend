package com.example.currency.asset.entity;

import com.example.currency.asset.enums.AssetType;
import com.example.currency.common.AuditableEntity;
import com.example.currency.watchlist.entity.WatchlistItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
		name = "assets",
		indexes = @Index(name = "assets_type_active_idx", columnList = "asset_type, is_active")
)
public class Asset extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 16)
	private String symbol;

	@Column(nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "asset_type", nullable = false, length = 32)
	private AssetType assetType;

	@Column(name = "currency_code", length = 3)
	private String currencyCode;

	@Column(name = "decimal_scale", nullable = false)
	private short decimalScale;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@OneToMany(mappedBy = "baseAsset", fetch = FetchType.LAZY)
	private Set<AssetPair> basePairs = new LinkedHashSet<>();

	@OneToMany(mappedBy = "quoteAsset", fetch = FetchType.LAZY)
	private Set<AssetPair> quotePairs = new LinkedHashSet<>();

	@OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
	private Set<WatchlistItem> watchlistItems = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public AssetType getAssetType() {
		return assetType;
	}

	public void setAssetType(AssetType assetType) {
		this.assetType = assetType;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public short getDecimalScale() {
		return decimalScale;
	}

	public void setDecimalScale(short decimalScale) {
		this.decimalScale = decimalScale;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Set<AssetPair> getBasePairs() {
		return basePairs;
	}

	public Set<AssetPair> getQuotePairs() {
		return quotePairs;
	}

	public Set<WatchlistItem> getWatchlistItems() {
		return watchlistItems;
	}

}
