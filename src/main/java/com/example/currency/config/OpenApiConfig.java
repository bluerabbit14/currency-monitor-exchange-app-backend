package com.example.currency.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI currencyOpenAPI() {
		return new OpenAPI().info(new Info()
				.title("Currency Monitor API")
				.description("Exchange rate monitoring, watchlists, and price alerts.")
				.version("v1"));
	}

}
