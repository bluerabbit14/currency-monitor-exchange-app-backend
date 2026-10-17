package com.example.currency.rate.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.provider.ProviderQuote;
import com.example.currency.rate.provider.ProviderRateLimitException;
import com.example.currency.rate.provider.goldapi.GoldApiClient;
import com.example.currency.rate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Collects commodity prices from Gold API, one paced request per mapped pair, and records them through
 * {@link RatePersistenceService}, the same pipeline the Twelve Data and CoinGecko collectors use.
 * <p>
 * A pair is collected only when it is ACTIVE, has {@code provider_code = GOLDAPI}, and its
 * {@code provider_pair_code} (the provider symbol, for example {@code XAU}) agrees with its assets: the base is an
 * active COMMODITY asset whose {@code external_id} is that symbol, and the quote is the active FIAT asset USD,
 * which is the currency Gold API quotes in.
 * <p>
 * Requests are sequential; the client spaces them and caps them per hour, and the collector asks for the remaining
 * budget before each fetch and rotates its starting commodity so every commodity is served in turn. Provider calls run outside any
 * transaction and each price is saved in its own short transaction, so a failure on one commodity never blocks
 * another and never touches a stored rate. A price whose {@code updatedAt} is not newer than the stored
 * {@code observed_at} is skipped, which keeps {@code rate_history} free of repeats (for example over a weekend
 * when the market is closed). A rate limit ends the run; there is no retry, the next scheduled run is the retry.
 * <p>
 * The run lock is in-process only; it is not a distributed lock.
 */
@Service
public class GoldApiCollectionService {

	public static final String PROVIDER_CODE = "GOLDAPI";

	private static final Logger log = LoggerFactory.getLogger(GoldApiCollectionService.class);

	private final AssetPairRepository assetPairRepository;
	private final ExchangeRateRepository exchangeRateRepository;
	private final RatePersistenceService ratePersistenceService;
	private final GoldApiClient client;
	private final Clock clock;
	private final ReentrantLock runLock = new ReentrantLock();
	/** Where the next run starts in the commodity list; only touched while holding the run lock. */
	private int cursor;

	public GoldApiCollectionService(
			AssetPairRepository assetPairRepository,
			ExchangeRateRepository exchangeRateRepository,
			RatePersistenceService ratePersistenceService,
			GoldApiClient client,
			Clock clock) {
		this.assetPairRepository = assetPairRepository;
		this.exchangeRateRepository = exchangeRateRepository;
		this.ratePersistenceService = ratePersistenceService;
		this.client = client;
		this.clock = clock;
	}

	/**
	 * Runs one collection pass. Returns immediately if a pass is already running in this application.
	 * In the summary, {@code skipped} covers unmapped pairs and prices not newer than the stored one,
	 * {@code failed} covers commodities with no usable price, and {@code notAttempted} is the commodities
	 * left unfetched after a rate limit.
	 */
	public CollectionSummary collect() {
		if (!runLock.tryLock()) {
			log.warn("Gold API collection skipped: the previous run is still in progress");
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
		log.info("Gold API collection started");

		int skipped = 0;
		Map<String, AssetPair> pairsBySymbol = new LinkedHashMap<>();
		List<AssetPair> candidates =
				assetPairRepository.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, PROVIDER_CODE);
		for (AssetPair pair : candidates) {
			String symbol = mappedSymbol(pair);
			if (symbol == null || pairsBySymbol.putIfAbsent(symbol, pair) != null) {
				skipped++;
			}
		}
		if (pairsBySymbol.isEmpty()) {
			CollectionSummary summary = new CollectionSummary(0, 0, skipped, 0, 0, false);
			logFinished(summary, startedAt);
			return summary;
		}

		Map<UUID, Instant> stored = storedObservedAt(pairsBySymbol.values());
		int succeeded = 0;
		int failed = 0;
		int rateLimited = 0;
		int notAttempted = 0;
		boolean limited = false;

		// Rotate the starting commodity between runs so a small hourly budget serves every commodity in turn
		// instead of always the first ones.
		List<Map.Entry<String, AssetPair>> ordered = List.copyOf(pairsBySymbol.entrySet());
		int start = cursor % ordered.size();
		int attempted = 0;

		for (int i = 0; i < ordered.size(); i++) {
			Map.Entry<String, AssetPair> entry = ordered.get((start + i) % ordered.size());
			AssetPair pair = entry.getValue();
			if (limited) {
				notAttempted++;
				continue;
			}
			if (client.remainingRequests() <= 0) {
				// The hourly budget is spent: nothing is requested and nothing is refused by the provider.
				limited = true;
				notAttempted++;
				log.info("Gold API hourly request budget is spent; {} will be fetched in a later run", pair.getSymbol());
				continue;
			}
			attempted++;
			try {
				ProviderQuote quote = client.fetchQuote(entry.getKey());
				Instant fetchedAt = clock.instant();
				// Policy shared with the other collectors: no provider time means observed_at = fetched_at.
				Instant observedAt = quote.observedAt() != null ? quote.observedAt() : fetchedAt;
				Instant current = stored.get(pair.getId());
				if (current != null && !observedAt.isAfter(current)) {
					skipped++;
					log.debug("Gold API price for pair {} is not newer than the stored one; skipping", pair.getSymbol());
					continue;
				}
				ratePersistenceService.recordObservation(new RateObservation(
						pair.getId(), quote.rate(), observedAt, fetchedAt, PROVIDER_CODE, quote.observationId()));
				succeeded++;
			}
			catch (ProviderRateLimitException ex) {
				rateLimited++;
				limited = true;
				attempted--; // not served: it goes first in the next run
				log.warn("Gold API rate limited at pair {}; not requesting more until {}",
						pair.getSymbol(), ex.getRetryAfter());
			}
			catch (RuntimeException ex) {
				failed++;
				// One bad commodity must not stop the rest; the last stored rate is left untouched.
				log.warn("Collecting pair {} from Gold API failed: {}", pair.getSymbol(), ex.getMessage());
			}
		}
		cursor = (start + attempted) % ordered.size();

		CollectionSummary summary = new CollectionSummary(succeeded, failed, skipped, rateLimited, notAttempted, false);
		logFinished(summary, startedAt);
		return summary;
	}

	/**
	 * The upper-case provider symbol for a pair, or null (with a warning) when its mapping is missing or does not
	 * agree with its assets.
	 */
	private String mappedSymbol(AssetPair pair) {
		String raw = pair.getProviderPairCode();
		if (raw == null || raw.isBlank()) {
			log.warn("Skipping pair {}: provider_pair_code is empty", pair.getSymbol());
			return null;
		}
		String symbol = raw.trim().toUpperCase(Locale.ROOT);
		Asset base = pair.getBaseAsset();
		Asset quote = pair.getQuoteAsset();

		if (base.getAssetType() != AssetType.COMMODITY || base.getStatus() != AssetStatus.ACTIVE
				|| !PROVIDER_CODE.equalsIgnoreCase(base.getProvider())
				|| base.getExternalId() == null || !symbol.equalsIgnoreCase(base.getExternalId().trim())) {
			log.warn("Skipping pair {}: base asset {} is not an active Gold API commodity with external_id '{}'",
					pair.getSymbol(), base.getSymbol(), symbol);
			return null;
		}
		if (quote.getAssetType() != AssetType.FIAT || quote.getStatus() != AssetStatus.ACTIVE
				|| !"USD".equalsIgnoreCase(quote.getCurrencyCode())) {
			log.warn("Skipping pair {}: Gold API prices are quoted in USD, but the quote asset is {}",
					pair.getSymbol(), quote.getSymbol());
			return null;
		}
		return symbol;
	}

	private Map<UUID, Instant> storedObservedAt(java.util.Collection<AssetPair> pairs) {
		List<UUID> ids = pairs.stream().map(AssetPair::getId).toList();
		Map<UUID, Instant> result = new HashMap<>();
		for (ExchangeRate rate : exchangeRateRepository.findAllById(ids)) {
			result.put(rate.getPairId(), rate.getObservedAt());
		}
		return result;
	}

	private void logFinished(CollectionSummary summary, long startedAt) {
		log.info("Gold API collection finished: succeeded={} failed={} skipped={} rateLimited={} notAttempted={} "
						+ "durationMs={}",
				summary.succeeded(), summary.failed(), summary.skipped(), summary.rateLimited(),
				summary.notAttempted(), (System.nanoTime() - startedAt) / 1_000_000L);
	}

}
