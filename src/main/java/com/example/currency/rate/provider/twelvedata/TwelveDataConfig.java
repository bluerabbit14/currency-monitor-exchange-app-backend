package com.example.currency.rate.provider.twelvedata;

import com.example.currency.rate.config.RateProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
class TwelveDataConfig {

	/**
	 * A dedicated client so the explicit timeouts never leak into other RestClient users.
	 */
	@Bean
	@Qualifier("twelveDataRestClient")
	RestClient twelveDataRestClient(RateProperties properties) {
		RateProperties.TwelveData settings = properties.getTwelvedata();
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
