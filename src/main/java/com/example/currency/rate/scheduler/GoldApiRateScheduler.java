package com.example.currency.rate.scheduler;

import com.example.currency.rate.service.GoldApiCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic commodity collection from Gold API, independent of the Twelve Data and CoinGecko schedulers.
 * Registered only when {@code app.rates.goldapi.enabled=true}. {@code fixedDelay} starts the next run only after
 * the previous one finished; the service's own in-process lock covers any other caller. Neither is a
 * distributed lock: with several application instances each would call Gold API on its own.
 */
@Component
@ConditionalOnProperty(prefix = "app.rates.goldapi", name = "enabled", havingValue = "true")
public class GoldApiRateScheduler {

	private static final Logger log = LoggerFactory.getLogger(GoldApiRateScheduler.class);

	private final GoldApiCollectionService collectionService;

	public GoldApiRateScheduler(GoldApiCollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@Scheduled(
			fixedDelayString = "${app.rates.goldapi.interval:PT10M}",
			initialDelayString = "${app.rates.goldapi.initial-delay:PT60S}")
	public void collectRates() {
		try {
			collectionService.collect();
		}
		catch (RuntimeException ex) {
			log.error("Gold API rate collection run failed unexpectedly", ex);
		}
	}

}
