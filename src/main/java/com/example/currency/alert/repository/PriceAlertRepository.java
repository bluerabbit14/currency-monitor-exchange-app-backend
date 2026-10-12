package com.example.currency.alert.repository;

import com.example.currency.alert.entity.PriceAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

	@Query(
			value = """
					SELECT a FROM PriceAlert a
					JOIN FETCH a.pair p
					JOIN FETCH p.baseAsset
					JOIN FETCH p.quoteAsset
					WHERE a.user.id = :userId
					""",
			countQuery = """
					SELECT COUNT(a) FROM PriceAlert a
					WHERE a.user.id = :userId
					"""
	)
	Page<PriceAlert> findByUserId(@Param("userId") UUID userId, Pageable pageable);

	@Query("""
			SELECT a FROM PriceAlert a
			JOIN FETCH a.pair p
			JOIN FETCH p.baseAsset
			JOIN FETCH p.quoteAsset
			WHERE a.id = :id AND a.user.id = :userId
			""")
	Optional<PriceAlert> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM PriceAlert a WHERE a.id = :id AND a.user.id = :userId")
	int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

}
