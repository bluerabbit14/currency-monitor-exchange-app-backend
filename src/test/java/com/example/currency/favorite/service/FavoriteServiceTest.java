package com.example.currency.favorite.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.favorite.dto.FavoriteAssetResponse;
import com.example.currency.favorite.entity.UserFavorite;
import com.example.currency.favorite.repository.FavoriteRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

	@Mock
	private FavoriteRepository favoriteRepository;

	@Mock
	private AssetRepository assetRepository;

	@Mock
	private UserRepository userRepository;

	private FavoriteService favoriteService;

	@BeforeEach
	void setUp() {
		favoriteService = new FavoriteService(favoriteRepository, assetRepository, userRepository);
	}

	@Test
	void addsFavoriteForActiveAsset() {
		UUID userId = UUID.randomUUID();
		Asset asset = activeAsset("BTC", "Bitcoin", AssetType.CRYPTO);
		User user = userReference(userId);
		UserFavorite saved = favorite(user, asset, Instant.parse("2026-10-12T04:00:00Z"));

		when(assetRepository.findByIdAndStatus(asset.getId(), AssetStatus.ACTIVE)).thenReturn(Optional.of(asset));
		when(favoriteRepository.findByUserIdAndAssetId(userId, asset.getId())).thenReturn(Optional.empty());
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(favoriteRepository.saveAndFlush(any(UserFavorite.class))).thenReturn(saved);

		FavoriteAssetResponse response = favoriteService.add(userId, asset.getId());

		assertEquals(new FavoriteAssetResponse(
				asset.getId(),
				"BTC",
				"Bitcoin",
				AssetType.CRYPTO,
				AssetStatus.ACTIVE,
				Instant.parse("2026-10-12T04:00:00Z")), response);
		verify(favoriteRepository).saveAndFlush(any(UserFavorite.class));
		verify(assetRepository, never()).delete(any());
	}

	@Test
	void duplicateAddIsIdempotent() {
		UUID userId = UUID.randomUUID();
		Asset asset = activeAsset("ETH", "Ethereum", AssetType.CRYPTO);
		UserFavorite existing = favorite(userReference(userId), asset, Instant.parse("2026-10-11T00:00:00Z"));

		when(assetRepository.findByIdAndStatus(asset.getId(), AssetStatus.ACTIVE)).thenReturn(Optional.of(asset));
		when(favoriteRepository.findByUserIdAndAssetId(userId, asset.getId())).thenReturn(Optional.of(existing));

		FavoriteAssetResponse response = favoriteService.add(userId, asset.getId());

		assertEquals(existing.getCreatedAt(), response.createdAt());
		assertEquals(asset.getId(), response.assetId());
		verify(favoriteRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).getReferenceById(any());
	}

	@Test
	void concurrentDuplicateAddReturnsExistingRow() {
		UUID userId = UUID.randomUUID();
		Asset asset = activeAsset("SOL", "Solana", AssetType.CRYPTO);
		User user = userReference(userId);
		UserFavorite existing = favorite(user, asset, Instant.parse("2026-10-10T12:00:00Z"));

		when(assetRepository.findByIdAndStatus(asset.getId(), AssetStatus.ACTIVE)).thenReturn(Optional.of(asset));
		when(favoriteRepository.findByUserIdAndAssetId(userId, asset.getId()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existing));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(favoriteRepository.saveAndFlush(any(UserFavorite.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		FavoriteAssetResponse response = favoriteService.add(userId, asset.getId());

		assertEquals(existing.getCreatedAt(), response.createdAt());
		assertEquals("SOL", response.symbol());
	}

	@Test
	void listsOwnFavoritesNewestFirst() {
		UUID userId = UUID.randomUUID();
		Asset btc = activeAsset("BTC", "Bitcoin", AssetType.CRYPTO);
		Asset eth = activeAsset("ETH", "Ethereum", AssetType.CRYPTO);
		UserFavorite newer = favorite(userReference(userId), btc, Instant.parse("2026-10-12T04:00:00Z"));
		UserFavorite older = favorite(userReference(userId), eth, Instant.parse("2026-10-11T04:00:00Z"));

		when(favoriteRepository.findActiveByUserIdOrderByCreatedAtDesc(userId))
				.thenReturn(List.of(newer, older));

		List<FavoriteAssetResponse> responses = favoriteService.list(userId);

		assertEquals(List.of(btc.getId(), eth.getId()), responses.stream().map(FavoriteAssetResponse::assetId).toList());
		assertEquals(Instant.parse("2026-10-12T04:00:00Z"), responses.get(0).createdAt());
		verify(favoriteRepository).findActiveByUserIdOrderByCreatedAtDesc(userId);
	}

	@Test
	void removesFavoriteWithoutDeletingAsset() {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(favoriteRepository.deleteByUserIdAndAssetId(userId, assetId)).thenReturn(1);

		favoriteService.remove(userId, assetId);

		verify(favoriteRepository).deleteByUserIdAndAssetId(userId, assetId);
		verify(assetRepository, never()).delete(any());
		verify(assetRepository, never()).deleteById(any());
	}

	@Test
	void rejectsMissingOrInactiveAsset() {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(assetRepository.findByIdAndStatus(assetId, AssetStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThrows(AssetNotFoundException.class, () -> favoriteService.add(userId, assetId));
		verify(favoriteRepository, never()).saveAndFlush(any());
	}

	private static Asset activeAsset(String symbol, String name, AssetType type) {
		Asset asset = new Asset();
		setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(type);
		asset.setDecimalScale((short) 8);
		asset.setStatus(AssetStatus.ACTIVE);
		return asset;
	}

	private static User userReference(UUID userId) {
		User user = new User();
		setField(user, "id", userId);
		return user;
	}

	private static UserFavorite favorite(User user, Asset asset, Instant createdAt) {
		UserFavorite favorite = new UserFavorite();
		setField(favorite, "id", UUID.randomUUID());
		setField(favorite, "createdAt", createdAt);
		favorite.setUser(user);
		favorite.setAsset(asset);
		return favorite;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> type = target.getClass();
		while (type != null) {
			try {
				var field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(target, value);
				return;
			}
			catch (NoSuchFieldException ex) {
				type = type.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new IllegalStateException(name);
	}

}
