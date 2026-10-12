package com.example.currency.auth.repository;

import com.example.currency.auth.entity.RefreshSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshSession> findByTokenHash(String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshSession s
			set s.revokedAt = :revokedAt
			where s.user.id = :userId and s.revokedAt is null
			""")
	int revokeActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

}
