package com.example.currency.rate.repository;

import com.example.currency.rate.entity.RateHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RateHistoryRepository extends JpaRepository<RateHistory, UUID> {

	@Query("""
			SELECT h FROM RateHistory h
			WHERE h.pair.id = :pairId
			  AND h.observedAt >= :from
			  AND h.observedAt < :to
			""")
	Page<RateHistory> findHistory(
			@Param("pairId") UUID pairId,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

	/** Observations of one pair in the closed interval [from, to], oldest first. */
	@Query("""
			SELECT h FROM RateHistory h
			WHERE h.pair.id = :pairId
			  AND h.observedAt >= :from
			  AND h.observedAt <= :to
			ORDER BY h.observedAt ASC, h.id ASC
			""")
	List<RateHistory> findBetweenAscending(
			@Param("pairId") UUID pairId,
			@Param("from") Instant from,
			@Param("to") Instant to);

	/** The newest observation of a pair at or before the given instant. */
	Optional<RateHistory> findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
			UUID pairId,
			Instant observedAt);

	Optional<RateHistory> findByPairIdAndProviderCodeAndProviderObservationId(
			UUID pairId,
			String providerCode,
			String providerObservationId);

}
