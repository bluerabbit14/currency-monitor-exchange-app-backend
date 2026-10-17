package com.example.currency.rate.service;

/**
 * Outcome of one collection run. {@code notAttempted} counts pairs left unfetched because their provider hit
 * a rate limit earlier in the run.
 */
public record CollectionSummary(int succeeded, int failed, int skipped, int rateLimited, int notAttempted,
								boolean overlapped) {

	static CollectionSummary skippedOverlap() {
		return new CollectionSummary(0, 0, 0, 0, 0, true);
	}

}
