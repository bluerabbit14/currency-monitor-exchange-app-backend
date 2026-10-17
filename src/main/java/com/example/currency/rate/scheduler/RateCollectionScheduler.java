package com.example.currency.rate.scheduler;

import com.example.currency.rate.service.RateCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic rate collection. Registered only when {@code app.rates.collection.enabled=true}.
 * {@code fixedDelay} starts the next run only after the previous one finished, so runs never overlap here;
 * the service's own lock covers any other caller.
 */
@Component
@ConditionalOnProperty(prefix = "app.rates.collection", name = "enabled", havingValue = "true")
public class RateCollectionScheduler {

	private static final Logger log = LoggerFactory.getLogger(RateCollectionScheduler.class);

	private final RateCollectionService collectionService;

	public RateCollectionScheduler(RateCollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@Scheduled(
			fixedDelayString = "${app.rates.collection.interval:PT6M}",
			initialDelayString = "${app.rates.collection.initial-delay:PT30S}")
	public void collectRates() {
		try {
			collectionService.collect();
		}
		catch (RuntimeException ex) {
			log.error("Rate collection run failed unexpectedly", ex);
		}
	}

}
