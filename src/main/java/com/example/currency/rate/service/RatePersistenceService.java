package com.example.currency.rate.service;

import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
import com.example.currency.rate.exception.InvalidRateObservationException;
import com.example.currency.rate.repository.ExchangeRateRepository;
import com.example.currency.rate.repository.RateHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class RatePersistenceService {

	private final ExchangeRateRepository exchangeRateRepository;
	private final RateHistoryRepository rateHistoryRepository;
	private final AssetPairRepository assetPairRepository;

	public RatePersistenceService(
			ExchangeRateRepository exchangeRateRepository,
			RateHistoryRepository rateHistoryRepository,
			AssetPairRepository assetPairRepository) {
		this.exchangeRateRepository = exchangeRateRepository;
		this.rateHistoryRepository = rateHistoryRepository;
		this.assetPairRepository = assetPairRepository;
	}

	/**
	 * Records a rate observation into history and updates the current-rate cache when newer.
	 * Intended for future collectors; not invoked by HTTP endpoints.
	 *
	 * @return true when the current-rate cache was inserted or updated
	 */
	@Transactional
	public boolean recordObservation(RateObservation observation) {
		validate(observation);

		AssetPair pair = assetPairRepository.findById(observation.pairId())
				.orElseThrow(() -> new InvalidRateObservationException("Unknown asset pair"));

		boolean historyInserted = insertHistoryIfNew(pair, observation);
		boolean cacheUpdated = upsertCurrentRate(pair, observation);
		return historyInserted || cacheUpdated;
	}

	private void validate(RateObservation observation) {
		if (observation == null) {
			throw new InvalidRateObservationException("Observation is required");
		}
		if (observation.pairId() == null) {
			throw new InvalidRateObservationException("pairId is required");
		}
		if (observation.rate() == null || observation.rate().compareTo(BigDecimal.ZERO) <= 0) {
			throw new InvalidRateObservationException("rate must be positive");
		}
		if (observation.observedAt() == null) {
			throw new InvalidRateObservationException("observedAt is required");
		}
		if (observation.fetchedAt() == null) {
			throw new InvalidRateObservationException("fetchedAt is required");
		}
		if (observation.providerCode() == null || observation.providerCode().isBlank()) {
			throw new InvalidRateObservationException("providerCode is required");
		}
	}

	private boolean insertHistoryIfNew(AssetPair pair, RateObservation observation) {
		String observationId = normalizeObservationId(observation.providerObservationId());
		if (observationId != null) {
			Optional<RateHistory> existing = rateHistoryRepository
					.findByPairIdAndProviderCodeAndProviderObservationId(
							pair.getId(),
							observation.providerCode().trim(),
							observationId);
			if (existing.isPresent()) {
				return false;
			}
		}

		RateHistory history = new RateHistory();
		history.setPair(pair);
		history.setRate(observation.rate());
		history.setObservedAt(observation.observedAt());
		history.setFetchedAt(observation.fetchedAt());
		history.setProviderCode(observation.providerCode().trim());
		history.setProviderObservationId(observationId);
		rateHistoryRepository.save(history);
		return true;
	}

	private boolean upsertCurrentRate(AssetPair pair, RateObservation observation) {
		Optional<ExchangeRate> existing = exchangeRateRepository.findById(pair.getId());
		if (existing.isPresent()) {
			ExchangeRate current = existing.get();
			if (!observation.observedAt().isAfter(current.getObservedAt())) {
				return false;
			}
			current.setRate(observation.rate());
			current.setObservedAt(observation.observedAt());
			current.setFetchedAt(observation.fetchedAt());
			current.setProviderCode(observation.providerCode().trim());
			exchangeRateRepository.save(current);
			return true;
		}

		ExchangeRate created = new ExchangeRate();
		created.setPair(pair);
		created.setRate(observation.rate());
		created.setObservedAt(observation.observedAt());
		created.setFetchedAt(observation.fetchedAt());
		created.setProviderCode(observation.providerCode().trim());
		exchangeRateRepository.save(created);
		return true;
	}

	private static String normalizeObservationId(String providerObservationId) {
		if (providerObservationId == null) {
			return null;
		}
		String trimmed = providerObservationId.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
