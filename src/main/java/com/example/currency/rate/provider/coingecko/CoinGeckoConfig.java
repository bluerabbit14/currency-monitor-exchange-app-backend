package com.example.currency.rate.provider.coingecko;

import com.example.currency.rate.config.RateProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
class CoinGeckoConfig {

	/**
	 * A dedicated client so the explicit timeouts never leak into other RestClient users.
	 */
	@Bean
	@Qualifier("coinGeckoRestClient")
	RestClient coinGeckoRestClient(RateProperties properties) {
		RateProperties.CoinGecko settings = properties.getCoingecko();
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(settings.getConnectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(settings.getReadTimeout());
		return RestClient.builder()
				.baseUrl(settings.getBaseUrl())
				.requestFactory(requestFactory)
				.build();
	}

}
