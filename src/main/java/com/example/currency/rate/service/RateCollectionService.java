package com.example.currency.rate.service;

import org.springframework.stereotype.Service;

/**
 * Reserved boundary for future rate-provider collection orchestration.
 * Provider-specific clients, retries, and scheduling will be added after a provider is selected.
 * Controllers must not call this service; collection stays independent of HTTP reads.
 */
@Service
public class RateCollectionService {

	private final RatePersistenceService ratePersistenceService;

	public RateCollectionService(RatePersistenceService ratePersistenceService) {
		this.ratePersistenceService = ratePersistenceService;
	}

	/**
	 * Placeholder for future collection runs. Intentionally empty until provider integration.
	 */
	public void collect() {
		// Scheduled collection will be implemented and enabled after provider integration.
		// ratePersistenceService will record provider observations when collection is enabled.
		if (ratePersistenceService == null) {
			throw new IllegalStateException("RatePersistenceService is required");
		}
	}

}
