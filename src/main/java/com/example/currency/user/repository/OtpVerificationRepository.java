package com.example.currency.user.repository;

import com.example.currency.user.entity.OtpVerification;
import com.example.currency.user.enums.OtpIdentifierType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

	Optional<OtpVerification> findFirstByIdentifierAndIdentifierTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(
			String identifier,
			OtpIdentifierType identifierType);

	List<OtpVerification> findByIdentifierAndIdentifierTypeAndVerifiedAtIsNull(
			String identifier,
			OtpIdentifierType identifierType);

}
