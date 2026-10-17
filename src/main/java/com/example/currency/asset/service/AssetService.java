package com.example.currency.asset.service;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.asset.repository.AssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AssetService {

	private final AssetRepository assetRepository;

	public AssetService(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	public Page<AssetResponse> listActive(AssetType type, String search, Pageable pageable) {
		return assetRepository.findActive(type, normalizeSearch(search), pageable)
				.map(AssetService::toResponse);
	}

	/** Empty when the asset is missing or no longer active. */
	public Optional<AssetResponse> findActive(UUID id) {
		return assetRepository.findByIdAndStatus(id, AssetStatus.ACTIVE).map(AssetService::toResponse);
	}

	public AssetResponse getActive(UUID id) {
		return assetRepository.findByIdAndStatus(id, AssetStatus.ACTIVE)
				.map(AssetService::toResponse)
				.orElseThrow(() -> new AssetNotFoundException(id));
	}

	private static AssetResponse toResponse(Asset asset) {
		return new AssetResponse(
				asset.getId(),
				asset.getSymbol(),
				asset.getName(),
				asset.getAssetType(),
				asset.getStatus());
	}

	static String normalizeSearch(String search) {
		if (search == null) {
			return null;
		}
		String trimmed = search.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}

}
