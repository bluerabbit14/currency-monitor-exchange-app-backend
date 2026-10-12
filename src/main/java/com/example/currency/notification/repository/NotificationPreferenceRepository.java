package com.example.currency.notification.repository;

import com.example.currency.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

	Optional<NotificationPreference> findByUserId(UUID userId);

}
