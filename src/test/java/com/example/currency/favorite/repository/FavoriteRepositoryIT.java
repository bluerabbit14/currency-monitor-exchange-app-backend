package com.example.currency.favorite.repository;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.favorite.entity.UserFavorite;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class FavoriteRepositoryIT {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	@Autowired
	private FavoriteRepository favoriteRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void rejectsDuplicateFavoritesForSameUserAndAsset() {
		User user = userRepository.saveAndFlush(activeUser("dup@example.com"));
		Asset asset = seededAsset("BTC");

		favoriteRepository.saveAndFlush(favorite(user, asset));

		assertThrows(DataIntegrityViolationException.class,
				() -> favoriteRepository.saveAndFlush(favorite(user, asset)));
	}

	@Test
	void listsActiveFavoritesNewestFirstAndExcludesInactiveAssets() {
		User user = userRepository.saveAndFlush(activeUser("list@example.com"));
		Asset btc = seededAsset("BTC");
		Asset eth = seededAsset("ETH");
		Asset inactive = assetRepository.saveAndFlush(crypto("DOGE", "Dogecoin", AssetStatus.INACTIVE));

		UserFavorite older = favoriteRepository.saveAndFlush(favorite(user, eth));
		UserFavorite newer = favoriteRepository.saveAndFlush(favorite(user, btc));
		favoriteRepository.saveAndFlush(favorite(user, inactive));

		updateCreatedAt(older.getId(), Instant.parse("2026-10-10T00:00:00Z"));
		updateCreatedAt(newer.getId(), Instant.parse("2026-10-12T00:00:00Z"));
		entityManager.clear();

		List<UserFavorite> favorites = favoriteRepository.findActiveByUserIdOrderByCreatedAtDesc(user.getId());

		assertEquals(List.of(btc.getId(), eth.getId()),
				favorites.stream().map(favorite -> favorite.getAsset().getId()).toList());
		assertTrue(favorites.stream().noneMatch(favorite -> favorite.getAsset().getSymbol().equals("DOGE")));
	}

	@Test
	void deleteIsScopedToUserAndDoesNotRemoveAsset() {
		User owner = userRepository.saveAndFlush(activeUser("owner@example.com"));
		User other = userRepository.saveAndFlush(activeUser("other@example.com"));
		Asset asset = seededAsset("SOL");

		favoriteRepository.saveAndFlush(favorite(owner, asset));
		favoriteRepository.saveAndFlush(favorite(other, asset));

		int deleted = favoriteRepository.deleteByUserIdAndAssetId(owner.getId(), asset.getId());

		assertEquals(1, deleted);
		assertFalse(favoriteRepository.existsByUserIdAndAssetId(owner.getId(), asset.getId()));
		assertTrue(favoriteRepository.existsByUserIdAndAssetId(other.getId(), asset.getId()));
		assertTrue(assetRepository.findById(asset.getId()).isPresent());
	}

	@Test
	void deletingUserCascadesFavorites() {
		User user = userRepository.saveAndFlush(activeUser("cascade@example.com"));
		Asset asset = seededAsset("USD");
		favoriteRepository.saveAndFlush(favorite(user, asset));
		UUID userId = user.getId();
		UUID assetId = asset.getId();

		userRepository.delete(user);
		userRepository.flush();
		entityManager.clear();

		assertFalse(favoriteRepository.existsByUserIdAndAssetId(userId, assetId));
		assertTrue(assetRepository.findById(assetId).isPresent());
	}

	private void updateCreatedAt(UUID favoriteId, Instant createdAt) {
		entityManager.createNativeQuery("UPDATE user_favorites SET created_at = :createdAt WHERE id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", favoriteId)
				.executeUpdate();
	}

	private Asset seededAsset(String symbol) {
		return assetRepository.findActive(null, symbol, PageRequest.of(0, 1))
				.getContent()
				.stream()
				.findFirst()
				.orElseThrow();
	}

	private static User activeUser(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static Asset crypto(String symbol, String name, AssetStatus status) {
		Asset asset = new Asset();
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(AssetType.CRYPTO);
		asset.setDecimalScale((short) 8);
		asset.setStatus(status);
		return asset;
	}

	private static UserFavorite favorite(User user, Asset asset) {
		UserFavorite favorite = new UserFavorite();
		favorite.setUser(user);
		favorite.setAsset(asset);
		return favorite;
	}

}
