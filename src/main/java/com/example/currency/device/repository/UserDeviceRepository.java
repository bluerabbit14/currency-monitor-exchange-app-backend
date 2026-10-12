package com.example.currency.device.repository;

import com.example.currency.user.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

	Optional<UserDevice> findByDeviceToken(String deviceToken);

	Optional<UserDevice> findByIdAndUserId(UUID id, UUID userId);

	@Query("""
			SELECT d FROM UserDevice d
			WHERE d.user.id = :userId
			ORDER BY d.lastSeenAt DESC NULLS LAST, d.createdAt DESC
			""")
	List<UserDevice> findByUserIdOrderByLastSeenAtDesc(@Param("userId") UUID userId);

}
