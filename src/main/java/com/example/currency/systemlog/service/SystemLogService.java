package com.example.currency.systemlog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Append-only audit trail. Each write runs in its own transaction so a failure entry survives the
 * caller's rollback, and a logging failure never breaks the business operation.
 */
@Service
public class SystemLogService {

	private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);

	private static final String INSERT = """
			INSERT INTO system_logs (id, event_type, category, status, channel, user_id, recipient, provider,
			                         provider_message_id, reference_type, reference_id, error_code,
			                         error_message, ip_address, metadata, created_at)
			VALUES (:id, :eventType, :category, :status, :channel, :userId, :recipient, :provider,
			        :providerMessageId, :referenceType, :referenceId, :errorCode,
			        :errorMessage, :ipAddress, CAST(:metadata AS jsonb), CURRENT_TIMESTAMP)
			""";

	private final JdbcClient jdbcClient;
	private final TransactionTemplate requiresNew;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public SystemLogService(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
		this.jdbcClient = jdbcClient;
		this.requiresNew = new TransactionTemplate(transactionManager);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public void log(SystemLogEvent.Builder builder) {
		log(builder.build());
	}

	public void log(SystemLogEvent event) {
		try {
			String metadata = event.metadata() == null ? null : jsonMapper.writeValueAsString(event.metadata());
			requiresNew.executeWithoutResult(tx -> jdbcClient.sql(INSERT)
					.param("id", UUID.randomUUID())
					.param("eventType", event.type().name())
					.param("category", event.type().category().name())
					.param("status", event.status().name())
					.param("channel", event.channel() == null ? null : event.channel().name())
					.param("userId", event.userId())
					.param("recipient", event.recipient())
					.param("provider", event.provider())
					.param("providerMessageId", event.providerMessageId())
					.param("referenceType", event.referenceType())
					.param("referenceId", event.referenceId())
					.param("errorCode", event.errorCode())
					.param("errorMessage", event.errorMessage())
					.param("ipAddress", event.ipAddress())
					.param("metadata", metadata)
					.update());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to write system log for {}", event.type(), ex);
		}
	}

}
