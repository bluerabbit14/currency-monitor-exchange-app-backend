package com.example.currency.notification.repository;

import com.example.currency.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	Page<Notification> findByUser_IdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	Optional<Notification> findByIdAndUser_Id(UUID id, UUID userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Notification n
			SET n.read = true, n.readAt = :readAt
			WHERE n.user.id = :userId AND n.read = false
			""")
	int markAllUnreadAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

	int deleteByIdAndUser_Id(UUID id, UUID userId);

}
