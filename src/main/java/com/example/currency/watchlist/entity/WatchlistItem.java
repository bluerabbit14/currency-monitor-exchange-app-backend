package com.example.currency.watchlist.entity;

import com.example.currency.asset.entity.Asset;
import com.example.currency.common.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(
		name = "watchlist_items",
		uniqueConstraints = @UniqueConstraint(
				name = "watchlist_items_watchlist_asset_uq",
				columnNames = {"watchlist_id", "asset_id"}
		),
		indexes = @Index(name = "watchlist_items_asset_id_idx", columnList = "asset_id")
)
public class WatchlistItem extends CreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "watchlist_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Watchlist watchlist;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private Asset asset;

	public UUID getId() {
		return id;
	}

	public Watchlist getWatchlist() {
		return watchlist;
	}

	public void setWatchlist(Watchlist watchlist) {
		this.watchlist = watchlist;
	}

	public Asset getAsset() {
		return asset;
	}

	public void setAsset(Asset asset) {
		this.asset = asset;
	}

}
