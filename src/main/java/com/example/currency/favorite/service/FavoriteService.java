package com.example.currency.favorite.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.favorite.dto.FavoriteAssetResponse;
import com.example.currency.favorite.entity.UserFavorite;
import com.example.currency.favorite.repository.FavoriteRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final AssetRepository assetRepository;
	private final UserRepository userRepository;

	public FavoriteService(
			FavoriteRepository favoriteRepository,
			AssetRepository assetRepository,
			UserRepository userRepository) {
		this.favoriteRepository = favoriteRepository;
		this.assetRepository = assetRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public FavoriteAssetResponse add(UUID userId, UUID assetId) {
		Asset asset = assetRepository.findByIdAndStatus(assetId, AssetStatus.ACTIVE)
				.orElseThrow(() -> new AssetNotFoundException(assetId));

		return favoriteRepository.findByUserIdAndAssetId(userId, assetId)
				.map(FavoriteService::toResponse)
				.orElseGet(() -> createFavorite(userId, asset));
	}

	@Transactional(readOnly = true)
	public List<FavoriteAssetResponse> list(UUID userId) {
		return favoriteRepository.findActiveByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(FavoriteService::toResponse)
				.toList();
	}

	@Transactional
	public void remove(UUID userId, UUID assetId) {
		favoriteRepository.deleteByUserIdAndAssetId(userId, assetId);
	}

	private FavoriteAssetResponse createFavorite(UUID userId, Asset asset) {
		UserFavorite favorite = new UserFavorite();
		favorite.setUser(userRepository.getReferenceById(userId));
		favorite.setAsset(asset);
		try {
			return toResponse(favoriteRepository.saveAndFlush(favorite));
		}
		catch (DataIntegrityViolationException ex) {
			return favoriteRepository.findByUserIdAndAssetId(userId, asset.getId())
					.map(FavoriteService::toResponse)
					.orElseThrow(() -> ex);
		}
	}

	private static FavoriteAssetResponse toResponse(UserFavorite favorite) {
		Asset asset = favorite.getAsset();
		return new FavoriteAssetResponse(
				asset.getId(),
				asset.getSymbol(),
				asset.getName(),
				asset.getAssetType(),
				asset.getStatus(),
				favorite.getCreatedAt());
	}

}
