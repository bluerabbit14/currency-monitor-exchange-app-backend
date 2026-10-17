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

	/**
	 * A crypto price in a fiat currency with no stored pair: the canonical CRYPTO/USD rate converted with the
	 * stored USD-fiat rate. Both inputs are cached rows; nothing is fetched and no rate is invented.
	 */
	private RateResponse getDerivedRate(Derivation derivation) {
		String base = derivation.cryptoPair().getBaseAsset().getSymbol();
		String quote = derivation.fiat().getSymbol();
		ExchangeRate crypto = exchangeRateRepository.findByPairIdWithAssets(derivation.cryptoPair().getId())
				.orElseThrow(() -> new RateNotAvailableException(base, quote));
		ExchangeRate fx = exchangeRateRepository.findByPairIdWithAssets(derivation.fxPair().getId())
				.orElseThrow(() -> new RateNotAvailableException(base, quote));

		BigDecimal converted;
		try {
			converted = FiatConversion.usdToFiat(
					crypto.getRate(), fx.getRate(), derivation.fxIsQuotePerUsd(), derivation.fiat().getDecimalScale());
		}
		catch (IllegalArgumentException ex) {
			throw new RateNotAvailableException(base, quote);
		}
		// The converted price is only as fresh as its older input.
		Instant observedAt = earlier(crypto.getObservedAt(), fx.getObservedAt());
		return new RateResponse(
				base,
				quote,
				converted,
				observedAt,
				earlier(crypto.getFetchedAt(), fx.getFetchedAt()),
				isStale(observedAt),
				crypto.getProviderCode() + "+" + fx.getProviderCode());
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
	 * Each canonical CRYPTO/USD observation is converted with the newest stored FX observation at or before
	 * its own {@code observedAt}, never with today's rate. An observation older than every stored FX
	 * observation has no rate to convert with and is left out of the page content; {@code totalElements}
	 * still counts the underlying CRYPTO/USD observations.
	 */
	private Page<RateHistoryResponse> getDerivedHistory(
			Derivation derivation, Instant from, Instant to, Pageable pageable) {
		Page<RateHistory> page = rateHistoryRepository.findHistory(
				derivation.cryptoPair().getId(), from, to, pageable);
		if (page.isEmpty()) {
			return page.map(RateQueryService::toHistoryResponse);
		}

		Instant oldest = page.stream().map(RateHistory::getObservedAt).min(Comparator.naturalOrder()).orElseThrow();
		Instant newest = page.stream().map(RateHistory::getObservedAt).max(Comparator.naturalOrder()).orElseThrow();
		List<RateHistory> fxObservations = new ArrayList<>();
		rateHistoryRepository.findFirstByPairIdAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
				derivation.fxPair().getId(), oldest).ifPresent(fxObservations::add);
		for (RateHistory fx : rateHistoryRepository.findBetweenAscending(
				derivation.fxPair().getId(), oldest, newest)) {
			if (fxObservations.isEmpty() || fx.getObservedAt().isAfter(fxObservations.getLast().getObservedAt())) {
				fxObservations.add(fx);
			}
			else if (fx.getObservedAt().equals(fxObservations.getLast().getObservedAt())) {
				fxObservations.set(fxObservations.size() - 1, fx);
			}
		}

		List<RateHistoryResponse> content = new ArrayList<>();
		for (RateHistory crypto : page) {
			RateHistory fx = newestAtOrBefore(fxObservations, crypto.getObservedAt());
			if (fx == null) {
				continue;
			}
			try {
				content.add(new RateHistoryResponse(
						FiatConversion.usdToFiat(crypto.getRate(), fx.getRate(),
								derivation.fxIsQuotePerUsd(), derivation.fiat().getDecimalScale()),
						crypto.getObservedAt(),
						earlier(crypto.getFetchedAt(), fx.getFetchedAt()),
						crypto.getProviderCode() + "+" + fx.getProviderCode()));
			}
			catch (IllegalArgumentException ex) {
				// A non-positive stored rate cannot be converted; leave the observation out.
			}
		}
		return new PageImpl<>(content, pageable, page.getTotalElements());
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

	/**
	 * Resolves how a CRYPTO/USD price becomes a price in {@code quote}: the base must be a crypto asset with an
	 * active {@code BASE/USD} pair, and the quote an active fiat asset with a stored FX pair against USD in
	 * either direction ({@code USD/XXX} multiplies, {@code XXX/USD} divides). Anything else is an unsupported pair.
	 */
	private Derivation derivation(String base, String quote) {
		if (USD.equals(quote)) {
			throw new UnsupportedPairException(base, quote);
		}
		AssetPair cryptoPair = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, base, USD)
				.filter(pair -> pair.getBaseAsset().getAssetType() == AssetType.CRYPTO)
				.orElseThrow(() -> new UnsupportedPairException(base, quote));

		Optional<AssetPair> usdToFiat = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, USD, quote);
		if (usdToFiat.isPresent() && usdToFiat.get().getQuoteAsset().getAssetType() == AssetType.FIAT) {
			return new Derivation(cryptoPair, usdToFiat.get(), usdToFiat.get().getQuoteAsset(), true);
		}
		Optional<AssetPair> fiatToUsd = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, quote, USD);
		if (fiatToUsd.isPresent() && fiatToUsd.get().getBaseAsset().getAssetType() == AssetType.FIAT) {
			return new Derivation(cryptoPair, fiatToUsd.get(), fiatToUsd.get().getBaseAsset(), false);
		}
		throw new UnsupportedPairException(base, quote);
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private record Derivation(AssetPair cryptoPair, AssetPair fxPair, Asset fiat, boolean fxIsQuotePerUsd) {
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
