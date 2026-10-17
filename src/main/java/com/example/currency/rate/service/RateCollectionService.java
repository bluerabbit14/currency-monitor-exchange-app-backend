package com.example.currency.rate.service;

import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.RateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fetches current prices for active, provider-mapped pairs and records them.
 * <p>
 * Provider calls run outside any transaction; each price is saved by {@link RatePersistenceService} in its own
 * short transaction, so a failure on one pair never rolls back or blocks another, and a failed fetch never
 * touches the last stored rate. Requests are sequential (concurrency 1) to respect provider quotas.
 * <p>
 * The run lock is in-process only. With several application instances each would collect independently and
 * spend provider credits twice; the primary key keeps {@code exchange_rates} to one row per pair, but
 * {@code rate_history} would gain duplicate observations when the provider supplies no observation id.
 */
@Service
public class RateCollectionService {

	private static final Logger log = LoggerFactory.getLogger(RateCollectionService.class);

	private final AssetPairRepository assetPairRepository;
	private final RatePersistenceService ratePersistenceService;
	private final Map<String, RateProvider> providers = new HashMap<>();
	private final Clock clock;
	private final ReentrantLock runLock = new ReentrantLock();

	public RateCollectionService(
			AssetPairRepository assetPairRepository,
			RatePersistenceService ratePersistenceService,
			List<RateProvider> providers,
			Clock clock) {
		this.assetPairRepository = assetPairRepository;
		this.ratePersistenceService = ratePersistenceService;
		this.clock = clock;
		for (RateProvider provider : providers) {
			this.providers.put(provider.code().toUpperCase(Locale.ROOT), provider);
		}
	}

	/**
	 * Runs one collection pass. Returns immediately if a pass is already running in this application.
	 */
	public CollectionSummary collect() {
		if (!runLock.tryLock()) {
			log.warn("Rate collection skipped: the previous run is still in progress");
			return CollectionSummary.skippedOverlap();
		}
		try {
			return runOnce();
		}
		finally {
			runLock.unlock();
		}
	}

	private CollectionSummary runOnce() {
		long startedAt = System.nanoTime();
		int skipped = 0;
		Map<RateProvider, List<AssetPair>> work = new LinkedHashMap<>();

		for (AssetPair pair : assetPairRepository.findByStatus(AssetStatus.ACTIVE)) {
			String providerCode = pair.getProviderCode();
			if (providerCode == null || providerCode.isBlank()) {
				skipped++;
				log.debug("Skipping pair {}: no provider mapping", pair.getSymbol());
				continue;
			}
			RateProvider provider = providers.get(providerCode.trim().toUpperCase(Locale.ROOT));
			if (provider == null) {
				skipped++;
				log.debug("Skipping pair {}: provider {} has no collector", pair.getSymbol(), providerCode);
				continue;
			}
			if (pair.getProviderPairCode() == null || pair.getProviderPairCode().isBlank()) {
				skipped++;
				log.warn("Skipping pair {}: provider {} is set but provider_pair_code is empty; "
						+ "set asset_pairs.provider_pair_code", pair.getSymbol(), providerCode);
				continue;
			}
			work.computeIfAbsent(provider, key -> new ArrayList<>()).add(pair);
		}

		int succeeded = 0;
		int failed = 0;
		int rateLimited = 0;
		int notAttempted = 0;

		for (Map.Entry<RateProvider, List<AssetPair>> entry : work.entrySet()) {
			RateProvider provider = entry.getKey();
			List<AssetPair> pairs = entry.getValue();
			if (!provider.isConfigured()) {
				skipped += pairs.size();
				log.warn("Skipping {} pair(s): provider {} is not configured (missing API key?)",
						pairs.size(), provider.code());
				continue;
			}
			boolean limited = false;
			for (AssetPair pair : pairs) {
				if (limited) {
					notAttempted++;
					continue;
				}
				try {
					ProviderQuote quote = provider.fetchQuote(pair.getProviderPairCode());
					Instant fetchedAt = clock.instant();
					// Policy: when the provider supplies no observation time, observed_at = fetched_at.
					Instant observedAt = quote.observedAt() != null ? quote.observedAt() : fetchedAt;
					ratePersistenceService.recordObservation(new RateObservation(
							pair.getId(), quote.rate(), observedAt, fetchedAt, provider.code(),
							quote.observationId()));
					succeeded++;
				}
				catch (ProviderRateLimitException ex) {
					rateLimited++;
					limited = true;
					log.warn("Provider {} rate limited at pair {}; not requesting more until {}",
							provider.code(), pair.getSymbol(), ex.getRetryAfter());
				}
				catch (RuntimeException ex) {
					failed++;
					// One bad pair must not stop the rest; the last stored rate is left untouched.
					log.warn("Collecting pair {} from {} failed: {}",
							pair.getSymbol(), provider.code(), ex.getMessage());
				}
			}
		}

		CollectionSummary summary = new CollectionSummary(succeeded, failed, skipped, rateLimited, notAttempted, false);
		log.info("Rate collection finished: succeeded={} failed={} skipped={} rateLimited={} notAttempted={} "
						+ "durationMs={}",
				succeeded, failed, skipped, rateLimited, notAttempted, (System.nanoTime() - startedAt) / 1_000_000L);
		return summary;
	}

}
