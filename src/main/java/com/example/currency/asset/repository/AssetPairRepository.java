package com.example.currency.asset.repository;

import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetPairRepository extends JpaRepository<AssetPair, UUID> {

	@Query("""
			SELECT p FROM AssetPair p
			JOIN FETCH p.baseAsset base
			JOIN FETCH p.quoteAsset quote
			WHERE p.status = :status
			  AND UPPER(base.symbol) = UPPER(:base)
			  AND UPPER(quote.symbol) = UPPER(:quote)
			""")
	Optional<AssetPair> findByStatusAndSymbols(
			@Param("status") AssetStatus status,
			@Param("base") String base,
			@Param("quote") String quote);

}
