package com.example.currency.plan.repository;

import com.example.currency.plan.entity.Plan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

	@EntityGraph(attributePaths = "entitlements")
	List<Plan> findByActiveTrueOrderByCreatedAtAscNameAsc();

	Optional<Plan> findByDefaultPlanTrue();

	/** The plan of the user's live subscription (latest period end wins), if any. */
	@Query(value = """
			SELECT p.* FROM plans p
			JOIN subscriptions s ON s.plan_id = p.id
			WHERE s.user_id = :userId
			  AND s.status IN ('ACTIVE', 'TRIAL', 'GRACE')
			  AND (s.current_period_end IS NULL OR s.current_period_end > :now)
			ORDER BY s.current_period_end DESC NULLS FIRST
			LIMIT 1
			""", nativeQuery = true)
	Optional<Plan> findCurrentForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
