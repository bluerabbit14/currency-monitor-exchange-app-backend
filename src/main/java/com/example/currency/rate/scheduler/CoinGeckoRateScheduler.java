package com.example.currency.rate.scheduler;

import com.example.currency.rate.service.CoinGeckoCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic CoinGecko collection, independent of {@link RateCollectionScheduler}. Registered only when
 * {@code app.rates.coingecko.enabled=true} and an API key is set. {@code fixedDelay} starts the next run only
 * after the previous one finished; the service's own in-process lock covers any other caller. Neither is a
 * distributed lock: with several application instances each would call CoinGecko and spend quota.
 */
@Component
@ConditionalOnExpression("${app.rates.coingecko.enabled:false} and !'${app.rates.coingecko.api-key:}'.isBlank()")
public class CoinGeckoRateScheduler {

	private static final Logger log = LoggerFactory.getLogger(CoinGeckoRateScheduler.class);

	private final CoinGeckoCollectionService collectionService;

	public CoinGeckoRateScheduler(CoinGeckoCollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@Scheduled(
			fixedDelayString = "${app.rates.coingecko.interval:PT5M}",
			initialDelayString = "${app.rates.coingecko.initial-delay:PT45S}")
	public void collectRates() {
		try {
			collectionService.collect();
		}
		catch (RuntimeException ex) {
			log.error("CoinGecko rate collection run failed unexpectedly", ex);
		}
	}

}
