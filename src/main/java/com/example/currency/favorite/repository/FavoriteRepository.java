package com.example.currency.favorite.repository;

import com.example.currency.favorite.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<UserFavorite, UUID> {

	boolean existsByUserIdAndAssetId(UUID userId, UUID assetId);

	@Query("SELECT f.asset.id FROM UserFavorite f WHERE f.user.id = :userId")
	List<UUID> findAssetIdsByUserId(@Param("userId") UUID userId);

	Optional<UserFavorite> findByUserIdAndAssetId(UUID userId, UUID assetId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			DELETE FROM UserFavorite f
			WHERE f.user.id = :userId
			  AND f.asset.id = :assetId
			""")
	int deleteByUserIdAndAssetId(@Param("userId") UUID userId, @Param("assetId") UUID assetId);

	@Query("""
			SELECT f FROM UserFavorite f
			JOIN FETCH f.asset a
			WHERE f.user.id = :userId
			  AND a.status = com.example.currency.asset.enums.AssetStatus.ACTIVE
			ORDER BY f.createdAt DESC
			""")
	List<UserFavorite> findActiveByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

}
