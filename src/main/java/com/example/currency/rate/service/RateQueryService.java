package com.example.currency.rate.service;

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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class RateQueryService {

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
		AssetPair pair = requireActivePair(normalizedBase, normalizedQuote);
		return exchangeRateRepository.findByPairIdWithAssets(pair.getId())
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
		AssetPair pair = requireActivePair(normalizedBase, normalizedQuote);

		Instant resolvedTo = to != null ? to : Instant.now(clock);
		Instant resolvedFrom = from != null ? from : resolvedTo.minus(DEFAULT_HISTORY_WINDOW);
		if (!resolvedFrom.isBefore(resolvedTo)) {
			throw new InvalidRateHistoryRangeException("from must be earlier than to");
		}

		return rateHistoryRepository.findHistory(pair.getId(), resolvedFrom, resolvedTo, pageable)
				.map(RateQueryService::toHistoryResponse);
	}

	private AssetPair requireActivePair(String base, String quote) {
		return assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, base, quote)
				.orElseThrow(() -> new UnsupportedPairException(base, quote));
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
