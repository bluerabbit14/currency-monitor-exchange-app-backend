package com.example.currency.asset.entity;

import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.common.AuditableEntity;
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
		indexes = @Index(name = "assets_type_status_idx", columnList = "asset_type, status")
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AssetStatus status = AssetStatus.ACTIVE;

	@Column(length = 64)
	private String provider;

	@Column(name = "external_id", length = 255)
	private String externalId;

	@OneToMany(mappedBy = "baseAsset", fetch = FetchType.LAZY)
	private Set<AssetPair> basePairs = new LinkedHashSet<>();

	@OneToMany(mappedBy = "quoteAsset", fetch = FetchType.LAZY)
	private Set<AssetPair> quotePairs = new LinkedHashSet<>();

	@OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
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

	public AssetStatus getStatus() {
		return status;
	}

	public void setStatus(AssetStatus status) {
		this.status = status;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public Set<AssetPair> getBasePairs() {
		return basePairs;
	}

	public Set<AssetPair> getQuotePairs() {
		return quotePairs;
	}

}
