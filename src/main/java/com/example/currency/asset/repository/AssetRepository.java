package com.example.currency.asset.repository;

import java.util.List;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

	@Query("""
			SELECT a FROM Asset a
			WHERE a.status = com.example.currency.asset.enums.AssetStatus.ACTIVE
			  AND (:type IS NULL OR a.assetType = :type)
			  AND (
			        CAST(:search AS String) IS NULL
			        OR LOWER(a.symbol) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')) ESCAPE '\\'
			        OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')) ESCAPE '\\'
			  )
			""")
	Page<Asset> findActive(@Param("type") AssetType type, @Param("search") String search, Pageable pageable);

	Optional<Asset> findByIdAndStatus(UUID id, AssetStatus status);

	List<Asset> findByStatusOrderByNameAsc(AssetStatus status);

	List<Asset> findByAssetTypeAndStatusOrderByNameAsc(AssetType assetType, AssetStatus status);

	Optional<Asset> findByIdAndAssetTypeAndStatus(UUID id, AssetType assetType, AssetStatus status);

}
