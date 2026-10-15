package com.example.currency.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI currencyOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Currency Monitor API")
						.description("Exchange rate monitoring, favorites, and price alerts.")
						.version("v1"))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.name(BEARER_AUTH)
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}

}
