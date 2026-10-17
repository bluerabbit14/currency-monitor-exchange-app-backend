package com.example.currency.deletion.service;

import com.example.currency.deletion.config.DeletionProperties;
import com.example.currency.systemlog.enums.SystemEventType;
import com.example.currency.systemlog.enums.SystemLogStatus;
import com.example.currency.systemlog.service.SystemLogEvent;
import com.example.currency.systemlog.service.SystemLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountPurgeScheduler {

	private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

	private final AccountPurgeService purgeService;
	private final DeletionProperties properties;
	private final SystemLogService systemLog;

	public AccountPurgeScheduler(
			AccountPurgeService purgeService,
			DeletionProperties properties,
			SystemLogService systemLog) {
		this.purgeService = purgeService;
		this.properties = properties;
		this.systemLog = systemLog;
	}

	@Scheduled(fixedDelayString = "${app.deletion.poll-interval-ms:60000}")
	public void purgeDueAccounts() {
		Instant now = Instant.now();
		for (UUID userId : purgeService.findDue(now, properties.getBatchSize())) {
			try {
				Optional<Boolean> kept = purgeService.purge(userId, now);
				kept.ifPresent(paymentsKept -> systemLog.log(
						SystemLogEvent.of(SystemEventType.ACCOUNT_PURGED, SystemLogStatus.SUCCESS)
								.reference("USER", userId)
								.meta("paymentRecordsKept", paymentsKept)));
			}
			catch (RuntimeException ex) {
				// One bad account must not block the rest; it is retried on the next poll.
				log.warn("Purging account {} failed", userId, ex);
				systemLog.log(SystemLogEvent.of(SystemEventType.ACCOUNT_PURGE_FAILED, SystemLogStatus.FAILURE)
						.userId(userId)
						.error("PURGE_FAILED", ex.getClass().getSimpleName()));
			}
		}
	}

}
