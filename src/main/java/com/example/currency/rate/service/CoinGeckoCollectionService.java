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
import com.example.currency.rate.provider.RateProviderException;
import com.example.currency.rate.provider.coingecko.CoinGeckoClient;
import com.example.currency.rate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects crypto prices from CoinGecko with one batched request per run and records them through
 * {@link RatePersistenceService}, the same pipeline the Twelve Data collector uses.
 * <p>
 * A pair is collected only when it is ACTIVE, has {@code provider_code = COINGECKO}, and its
 * {@code provider_pair_code} ({@code <coin id>:<vs currency>}) agrees with its assets: the base is an active
 * CRYPTO asset whose {@code external_id} is that coin id, and the quote is an active FIAT asset whose currency
 * code is that currency. Coins the provider returns beyond the configured pairs are ignored.
 * <p>
 * The provider call runs outside any transaction and each price is saved in its own short transaction, so one
 * bad pair never blocks another and a failed fetch never touches a stored rate. A price whose
 * {@code last_updated_at} is not newer than the stored {@code observed_at} is skipped, which keeps
 * {@code rate_history} free of repeats when CoinGecko has not refreshed a coin. There is no retry: the next
 * scheduled run is the retry.
 * <p>
 * The run lock is in-process only; it is not a distributed lock.
 */
@Service
public class CoinGeckoCollectionService {

	public static final String PROVIDER_CODE = "COINGECKO";

	private static final Logger log = LoggerFactory.getLogger(CoinGeckoCollectionService.class);

	private static final Pattern PAIR_CODE = Pattern.compile("^([a-z0-9][a-z0-9._-]*):([a-z]{2,10})$");

	private final AssetPairRepository assetPairRepository;
	private final ExchangeRateRepository exchangeRateRepository;
	private final RatePersistenceService ratePersistenceService;
	private final CoinGeckoClient client;
	private final Clock clock;
	private final ReentrantLock runLock = new ReentrantLock();

	public CoinGeckoCollectionService(
			AssetPairRepository assetPairRepository,
			ExchangeRateRepository exchangeRateRepository,
			RatePersistenceService ratePersistenceService,
			CoinGeckoClient client,
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
	 * {@code failed} covers pairs with no usable price, and {@code notAttempted} is the pairs held back
	 * by a rate limit.
	 */
	public CollectionSummary collect() {
		if (!runLock.tryLock()) {
			log.warn("CoinGecko collection skipped: the previous run is still in progress");
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
		log.info("CoinGecko collection started");

		if (!client.isConfigured()) {
			log.warn("CoinGecko collection skipped: COINGECKO_API_KEY is not configured");
			return new CollectionSummary(0, 0, 0, 0, 0, false);
		}

		int skipped = 0;
		Map<String, AssetPair> pairsByCode = new LinkedHashMap<>();
		Set<String> coinIds = new LinkedHashSet<>();
		Set<String> currencies = new LinkedHashSet<>();

		List<AssetPair> candidates =
				assetPairRepository.findByStatusAndProviderCodeWithAssets(AssetStatus.ACTIVE, PROVIDER_CODE);
		for (AssetPair pair : candidates) {
			String code = mappedCode(pair);
			if (code == null || pairsByCode.putIfAbsent(code, pair) != null) {
				skipped++;
				continue;
			}
			coinIds.add(code.substring(0, code.indexOf(':')));
			currencies.add(code.substring(code.indexOf(':') + 1));
		}

		if (pairsByCode.isEmpty()) {
			CollectionSummary summary = new CollectionSummary(0, 0, skipped, 0, 0, false);
			logFinished(summary, startedAt);
			return summary;
		}

		Map<String, ProviderQuote> quotes;
		try {
			quotes = client.fetchPrices(coinIds, currencies);
		}
		catch (ProviderRateLimitException ex) {
			log.warn("CoinGecko rate limited or held back; not requesting until {}", ex.getRetryAfter());
			CollectionSummary summary = new CollectionSummary(0, 0, skipped, 1, pairsByCode.size(), false);
			logFinished(summary, startedAt);
			return summary;
		}
		catch (RateProviderException ex) {
			log.warn("CoinGecko request failed: {}", ex.getMessage());
			CollectionSummary summary = new CollectionSummary(0, pairsByCode.size(), skipped, 0, 0, false);
			logFinished(summary, startedAt);
			return summary;
		}

		Map<UUID, Instant> stored = storedObservedAt(pairsByCode.values());
		Instant fetchedAt = clock.instant();
		int succeeded = 0;
		int failed = 0;

		for (Map.Entry<String, AssetPair> entry : pairsByCode.entrySet()) {
			AssetPair pair = entry.getValue();
			ProviderQuote quote = quotes.get(entry.getKey());
			if (quote == null) {
				failed++;
				log.warn("CoinGecko returned no usable price for pair {} ({})", pair.getSymbol(), entry.getKey());
				continue;
			}
			// Policy shared with Twelve Data: no provider time means observed_at = fetched_at.
			Instant observedAt = quote.observedAt() != null ? quote.observedAt() : fetchedAt;
			Instant current = stored.get(pair.getId());
			if (current != null && !observedAt.isAfter(current)) {
				skipped++;
				log.debug("CoinGecko price for pair {} is not newer than the stored one; skipping", pair.getSymbol());
				continue;
			}
			try {
				ratePersistenceService.recordObservation(new RateObservation(
						pair.getId(), quote.rate(), observedAt, fetchedAt, PROVIDER_CODE, quote.observationId()));
				succeeded++;
			}
			catch (RuntimeException ex) {
				failed++;
				// One bad pair must not stop the rest; the last stored rate is left untouched.
				log.warn("Recording CoinGecko pair {} failed: {}", pair.getSymbol(), ex.getMessage());
			}
		}

		CollectionSummary summary = new CollectionSummary(succeeded, failed, skipped, 0, 0, false);
		logFinished(summary, startedAt);
		return summary;
	}

	/**
	 * The lower-case {@code <coin id>:<vs currency>} request key for a pair, or null (with a warning) when the
	 * pair's mapping is missing or does not agree with its assets.
	 */
	private String mappedCode(AssetPair pair) {
		String raw = pair.getProviderPairCode();
		Matcher matcher = raw == null ? null : PAIR_CODE.matcher(raw.trim().toLowerCase(Locale.ROOT));
		if (matcher == null || !matcher.matches()) {
			log.warn("Skipping pair {}: provider_pair_code is not '<coingecko id>:<currency>'", pair.getSymbol());
			return null;
		}
		String coinId = matcher.group(1);
		String currency = matcher.group(2);
		Asset base = pair.getBaseAsset();
		Asset quote = pair.getQuoteAsset();

		if (base.getAssetType() != AssetType.CRYPTO || base.getStatus() != AssetStatus.ACTIVE
				|| !PROVIDER_CODE.equalsIgnoreCase(base.getProvider())
				|| base.getExternalId() == null || !coinId.equalsIgnoreCase(base.getExternalId().trim())) {
			log.warn("Skipping pair {}: base asset {} is not an active CoinGecko crypto asset with external_id '{}'",
					pair.getSymbol(), base.getSymbol(), coinId);
			return null;
		}
		if (quote.getAssetType() != AssetType.FIAT || quote.getStatus() != AssetStatus.ACTIVE
				|| quote.getCurrencyCode() == null || !currency.equalsIgnoreCase(quote.getCurrencyCode())) {
			log.warn("Skipping pair {}: quote asset {} is not an active fiat asset for currency '{}'",
					pair.getSymbol(), quote.getSymbol(), currency);
			return null;
		}
		return coinId + ":" + currency;
	}

	private Map<UUID, Instant> storedObservedAt(Collection<AssetPair> pairs) {
		List<UUID> ids = pairs.stream().map(AssetPair::getId).toList();
		Map<UUID, Instant> result = new HashMap<>();
		for (ExchangeRate rate : exchangeRateRepository.findAllById(ids)) {
			result.put(rate.getPairId(), rate.getObservedAt());
		}
		return result;
	}

	private void logFinished(CollectionSummary summary, long startedAt) {
		log.info("CoinGecko collection finished: succeeded={} failed={} skipped={} rateLimited={} notAttempted={} "
						+ "durationMs={}",
				summary.succeeded(), summary.failed(), summary.skipped(), summary.rateLimited(),
				summary.notAttempted(), (System.nanoTime() - startedAt) / 1_000_000L);
	}

}
