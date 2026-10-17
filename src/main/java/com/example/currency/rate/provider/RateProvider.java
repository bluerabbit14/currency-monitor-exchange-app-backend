package com.example.currency.rate.provider;

/**
 * A source of current prices. Implementations own their provider-specific quota handling and must throw
 * {@link RateProviderException} (never return a zero or placeholder price) when no valid price is available.
 */
public interface RateProvider {

	/**
	 * Matches {@code asset_pairs.provider_code}.
	 */
	String code();

	/**
	 * False when the provider cannot be called at all (for example a missing API key).
	 */
	default boolean isConfigured() {
		return true;
	}

	/**
	 * @param providerPairCode the pair's {@code provider_pair_code}, exactly as stored
	 * @throws ProviderRateLimitException when the provider's quota is exhausted or the call was held back locally
	 * @throws RateProviderException for any other failure or unusable response
	 */
	ProviderQuote fetchQuote(String providerPairCode);

}
