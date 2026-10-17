package com.example.currency.rate.service;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.dto.RateHistoryResponse;
import com.example.currency.rate.dto.RateResponse;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
import com.example.currency.rate.exception.InvalidRateHistoryRangeException;
import com.example.currency.rate.exception.RateNotAvailableException;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.rate.repository.ExchangeRateRepository;
import com.example.currency.rate.repository.RateHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RateQueryService {

	private static final String USD = "USD";
	private static final Duration DEFAULT_HISTORY_WINDOW = Duration.ofDays(30);

	private final ExchangeRateRepository exchangeRateRepository;
	private final RateHistoryRepository rateHistoryRepository;
	private final AssetPairRepository assetPairRepository;
	private final RateProperties rateProperties;
	private final Clock clock;

	public RateQueryService(
			ExchangeRateRepository exchangeRateRepository,
			RateHistoryRepository rateHistoryRepository,
			AssetPairRepository assetPairRepository,
			RateProperties rateProperties,
			Clock clock) {
		this.exchangeRateRepository = exchangeRateRepository;
		this.rateHistoryRepository = rateHistoryRepository;
		this.assetPairRepository = assetPairRepository;
		this.rateProperties = rateProperties;
		this.clock = clock;
	}

	public Page<RateResponse> listCurrentRates(AssetType type, String base, String quote, Pageable pageable) {
		return exchangeRateRepository.findCurrentRates(
						AssetStatus.ACTIVE,
						type,
						normalizeSymbol(base),
						normalizeSymbol(quote),
						pageable)
				.map(this::toRateResponse);
	}

	public RateResponse getCurrentRate(String base, String quote) {
		String normalizedBase = requireSymbol(base, "base");
		String normalizedQuote = requireSymbol(quote, "quote");
		Optional<AssetPair> direct = assetPairRepository.findByStatusAndSymbols(
				AssetStatus.ACTIVE, normalizedBase, normalizedQuote);
		if (direct.isEmpty()) {
			return getDerivedRate(derivation(normalizedBase, normalizedQuote));
		}
		return exchangeRateRepository.findByPairIdWithAssets(direct.get().getId())
				.map(this::toRateResponse)
				.orElseThrow(() -> new RateNotAvailableException(normalizedBase, normalizedQuote));
	}

	public Page<RateHistoryResponse> getHistory(
			String base,
			String quote,
			Instant from,
			Instant to,
			Pageable pageable) {
		String normalizedBase = requireSymbol(base, "base");
		String normalizedQuote = requireSymbol(quote, "quote");
		Optional<AssetPair> direct = assetPairRepository.findByStatusAndSymbols(
				AssetStatus.ACTIVE, normalizedBase, normalizedQuote);
		Derivation derivation = direct.isPresent() ? null : derivation(normalizedBase, normalizedQuote);

		Instant resolvedTo = to != null ? to : Instant.now(clock);
		Instant resolvedFrom = from != null ? from : resolvedTo.minus(DEFAULT_HISTORY_WINDOW);
		if (!resolvedFrom.isBefore(resolvedTo)) {
			throw new InvalidRateHistoryRangeException("from must be earlier than to");
		}

		if (derivation == null) {
			return rateHistoryRepository.findHistory(direct.get().getId(), resolvedFrom, resolvedTo, pageable)
					.map(RateQueryService::toHistoryResponse);
		}
		return getDerivedHistory(derivation, resolvedFrom, resolvedTo, pageable);
	}

	/**
	 * A price with no stored pair: crypto or fiat base, fiat quote. Both sides are valued in USD from their
	 * cached USD legs and divided. Both inputs are cached rows; nothing is fetched and no rate is invented.
	 */
	private RateResponse getDerivedRate(Derivation derivation) {
		String base = derivation.baseSymbol();
		String quote = derivation.quoteAsset().getSymbol();
		ExchangeRate baseRate = derivation.base() == null ? null
				: exchangeRateRepository.findByPairIdWithAssets(derivation.base().pair().getId())
						.orElseThrow(() -> new RateNotAvailableException(base, quote));
		ExchangeRate quoteRate = derivation.quote() == null ? null
				: exchangeRateRepository.findByPairIdWithAssets(derivation.quote().pair().getId())
						.orElseThrow(() -> new RateNotAvailableException(base, quote));

		BigDecimal converted;
		try {
			converted = convert(derivation, baseRate == null ? null : baseRate.getRate(),
					quoteRate == null ? null : quoteRate.getRate());
		}
		catch (IllegalArgumentException ex) {
			throw new RateNotAvailableException(base, quote);
		}
		List<ExchangeRate> inputs = new ArrayList<>();
		if (baseRate != null) {
			inputs.add(baseRate);
		}
		if (quoteRate != null) {
			inputs.add(quoteRate);
		}
		// The converted price is only as fresh as its oldest input.
		Instant observedAt = inputs.stream().map(ExchangeRate::getObservedAt).min(Comparator.naturalOrder()).orElseThrow();
		Instant fetchedAt = inputs.stream().map(ExchangeRate::getFetchedAt).min(Comparator.naturalOrder()).orElseThrow();
		return new RateResponse(base, quote, converted, observedAt, fetchedAt, isStale(observedAt),
				joinProviders(inputs.stream().map(ExchangeRate::getProviderCode).toList()));
	}

	/**
	 * Each observation of the first available leg is converted with the newest stored observation of the other
	 * leg at or before its own {@code observedAt}, never with today's rate. An observation older than every
	 * stored observation of the other leg has no rate to convert with and is left out of the page content;
	 * {@code totalElements} still counts the underlying observations.
	 */
	private Page<RateHistoryResponse> getDerivedHistory(
			Derivation derivation, Instant from, Instant to, Pageable pageable) {
		boolean rowsFromBase = derivation.base() != null;
		Leg source = rowsFromBase ? derivation.base() : derivation.quote();
		Leg other = rowsFromBase ? derivation.quote() : null;

		Page<RateHistory> page = rateHistoryRepository.findHistory(source.pair().getId(), from, to, pageable);
		if (page.isEmpty()) {
			return page.map(RateQueryService::toHistoryResponse);
		}

		List<RateHistory> otherObservations = other == null ? List.of() : observationsCovering(other, page);

		List<RateHistoryResponse> content = new ArrayList<>();
		for (RateHistory row : page) {
			RateHistory match = null;
			if (other != null) {
				match = newestAtOrBefore(otherObservations, row.getObservedAt());
				if (match == null) {
					continue;
				}
			}
			try {
				BigDecimal baseRate = rowsFromBase ? row.getRate() : null;
				BigDecimal quoteRate = rowsFromBase ? (match == null ? null : match.getRate()) : row.getRate();
				List<String> codes = new ArrayList<>(List.of(row.getProviderCode()));
				Instant fetchedAt = row.getFetchedAt();
				if (match != null) {
					codes.add(match.getProviderCode());
					fetchedAt = earlier(fetchedAt, match.getFetchedAt());
				}
				content.add(new RateHistoryResponse(
						convert(derivation, baseRate, quoteRate), row.getObservedAt(), fetchedAt, joinProviders(codes)));
			}
			catch (IllegalArgumentException ex) {
				// A non-positive stored rate cannot be converted; leave the observation out.
			}
		}
		return new PageImpl<>(content, pageable, page.getTotalElements());
	}

	/** The leg's observations from just before the page's oldest row to its newest row, oldest first. */
	private List<RateHistory> observationsCovering(Leg leg, Page<RateHistory> page) {
		Instant oldest = page.stream().map(RateHistory::getObservedAt).min(Comparator.naturalOrder()).orElseThrow();
		Instant newest = page.stream().map(RateHistory::getObservedAt).max(Comparator.naturalOrder()).orElseThrow();
		List<RateHistory> observations = new ArrayList<>();
		rateHistoryRepository.findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
				leg.pair().getId(), oldest).ifPresent(observations::add);
		for (RateHistory next : rateHistoryRepository.findBetweenAscending(leg.pair().getId(), oldest, newest)) {
			if (observations.isEmpty() || next.getObservedAt().isAfter(observations.getLast().getObservedAt())) {
				observations.add(next);
			}
			else if (next.getObservedAt().equals(observations.getLast().getObservedAt())) {
				observations.set(observations.size() - 1, next);
			}
		}
		return observations;
	}

	private static RateHistory newestAtOrBefore(List<RateHistory> ascending, Instant at) {
		RateHistory found = null;
		for (RateHistory candidate : ascending) {
			if (candidate.getObservedAt().isAfter(at)) {
				break;
			}
			found = candidate;
		}
		return found;
	}

	private static BigDecimal convert(Derivation derivation, BigDecimal baseRate, BigDecimal quoteRate) {
		return FiatConversion.cross(
				baseRate, derivation.base() != null && derivation.base().usdPerUnit(),
				quoteRate, derivation.quote() != null && derivation.quote().usdPerUnit(),
				derivation.quoteAsset().getDecimalScale());
	}

	private static String joinProviders(List<String> codes) {
		return String.join("+", codes.stream().distinct().toList());
	}

	/**
	 * Resolves how {@code base/quote} can be priced from USD legs. The base is a crypto or fiat asset and the
	 * quote a fiat asset; USD itself needs no leg. A leg is the asset's stored pair against USD in either
	 * direction ({@code XXX/USD} is USD per unit, {@code USD/XXX} is units per USD). Anything else, including
	 * commodities and an asset against itself, is an unsupported pair.
	 */
	private Derivation derivation(String base, String quote) {
		if (base.equals(quote)) {
			throw new UnsupportedPairException(base, quote);
		}
		Leg baseLeg = leg(base, true, base, quote);
		Leg quoteLeg = leg(quote, false, base, quote);
		if (baseLeg == null && quoteLeg == null) {
			throw new UnsupportedPairException(base, quote);
		}
		Asset quoteAsset = quoteLeg != null ? quoteLeg.asset() : usdSide(baseLeg.pair());
		return new Derivation(baseLeg, quoteLeg, base, quoteAsset);
	}

	/** Null for USD. */
	private Leg leg(String symbol, boolean allowCrypto, String base, String quote) {
		if (USD.equals(symbol)) {
			return null;
		}
		Optional<AssetPair> assetUsd = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, symbol, USD);
		if (assetUsd.isPresent() && supported(assetUsd.get().getBaseAsset(), allowCrypto)) {
			return new Leg(assetUsd.get(), assetUsd.get().getBaseAsset(), true);
		}
		Optional<AssetPair> usdAsset = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, USD, symbol);
		if (usdAsset.isPresent() && supported(usdAsset.get().getQuoteAsset(), allowCrypto)) {
			return new Leg(usdAsset.get(), usdAsset.get().getQuoteAsset(), false);
		}
		throw new UnsupportedPairException(base, quote);
	}

	private static boolean supported(Asset asset, boolean allowCrypto) {
		return asset.getAssetType() == AssetType.FIAT || (allowCrypto && asset.getAssetType() == AssetType.CRYPTO);
	}

	private static Asset usdSide(AssetPair pair) {
		return USD.equals(pair.getQuoteAsset().getSymbol()) ? pair.getQuoteAsset() : pair.getBaseAsset();
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	/** A stored pair against USD for one non-USD asset; {@code usdPerUnit} is true for {@code ASSET/USD}. */
	private record Leg(AssetPair pair, Asset asset, boolean usdPerUnit) {
	}

	/** Either leg is null when that side is USD. */
	private record Derivation(Leg base, Leg quote, String baseSymbol, Asset quoteAsset) {
	}

	private RateResponse toRateResponse(ExchangeRate rate) {
		AssetPair pair = rate.getPair();
		return new RateResponse(
				pair.getBaseAsset().getSymbol(),
				pair.getQuoteAsset().getSymbol(),
				rate.getRate(),
				rate.getObservedAt(),
				rate.getFetchedAt(),
				isStale(rate.getObservedAt()),
				rate.getProviderCode());
	}

	private boolean isStale(Instant observedAt) {
		Instant threshold = Instant.now(clock).minus(rateProperties.getStaleAfter());
		return observedAt.isBefore(threshold);
	}

	private static RateHistoryResponse toHistoryResponse(RateHistory history) {
		return new RateHistoryResponse(
				history.getRate(),
				history.getObservedAt(),
				history.getFetchedAt(),
				history.getProviderCode());
	}

	static String normalizeSymbol(String symbol) {
		if (symbol == null) {
			return null;
		}
		String trimmed = symbol.trim();
		return trimmed.isEmpty() ? null : trimmed.toUpperCase();
	}

	private static String requireSymbol(String symbol, String field) {
		String normalized = normalizeSymbol(symbol);
		if (normalized == null) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return normalized;
	}

}
