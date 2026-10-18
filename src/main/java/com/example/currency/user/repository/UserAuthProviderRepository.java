package com.example.currency.user.repository;

import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, UUID> {

	Optional<UserAuthProvider> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

	boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);

}
