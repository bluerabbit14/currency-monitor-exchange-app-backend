package com.example.currency.rate.repository;

import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.rate.entity.ExchangeRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

	@Query(
			value = """
					SELECT r FROM ExchangeRate r
					JOIN FETCH r.pair p
					JOIN FETCH p.baseAsset base
					JOIN FETCH p.quoteAsset quote
					WHERE p.status = :status
					  AND (:type IS NULL OR base.assetType = :type)
					  AND (:base IS NULL OR UPPER(base.symbol) = UPPER(:base))
					  AND (:quote IS NULL OR UPPER(quote.symbol) = UPPER(:quote))
					""",
			countQuery = """
					SELECT COUNT(r) FROM ExchangeRate r
					JOIN r.pair p
					JOIN p.baseAsset base
					JOIN p.quoteAsset quote
					WHERE p.status = :status
					  AND (:type IS NULL OR base.assetType = :type)
					  AND (:base IS NULL OR UPPER(base.symbol) = UPPER(:base))
					  AND (:quote IS NULL OR UPPER(quote.symbol) = UPPER(:quote))
					"""
	)
	Page<ExchangeRate> findCurrentRates(
			@Param("status") AssetStatus status,
			@Param("type") AssetType type,
			@Param("base") String base,
			@Param("quote") String quote,
			Pageable pageable);

	@Query("""
			SELECT r FROM ExchangeRate r
			JOIN FETCH r.pair p
			JOIN FETCH p.baseAsset base
			JOIN FETCH p.quoteAsset quote
			WHERE p.id = :pairId
			""")
	Optional<ExchangeRate> findByPairIdWithAssets(@Param("pairId") UUID pairId);

}
