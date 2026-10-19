package com.example.currency.asset.service;

import com.example.currency.asset.dto.CurrencyItemResponse;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.favorite.repository.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CurrencyCatalogService {

	private final AssetRepository assetRepository;
	private final FavoriteRepository favoriteRepository;

	public CurrencyCatalogService(AssetRepository assetRepository, FavoriteRepository favoriteRepository) {
		this.assetRepository = assetRepository;
		this.favoriteRepository = favoriteRepository;
	}

	/**
	 * Every active asset grouped by type, each group sorted by name. Every {@link AssetType} is present, empty
	 * when it has no active assets. {@code userId} may be null, in which case nothing is flagged as a favorite.
	 */
	public Map<AssetType, List<CurrencyItemResponse>> listGroupedByType(UUID userId) {
		Set<UUID> favoriteIds = userId == null
				? Set.of()
				: new HashSet<>(favoriteRepository.findAssetIdsByUserId(userId));
		Map<AssetType, List<CurrencyItemResponse>> grouped = new EnumMap<>(AssetType.class);
		for (AssetType type : AssetType.values()) {
			grouped.put(type, new ArrayList<>());
		}
		for (Asset asset : assetRepository.findByStatusOrderByNameAsc(AssetStatus.ACTIVE)) {
			grouped.get(asset.getAssetType()).add(new CurrencyItemResponse(
					asset.getId(),
					asset.getSymbol(),
					asset.getName(),
					favoriteIds.contains(asset.getId()),
					asset.getStatus()));
		}
		return grouped;
	}

}
