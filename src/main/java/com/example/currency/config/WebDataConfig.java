package com.example.currency.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebDataConfig {

	@Bean
	PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer() {
		return resolver -> resolver.setMaxPageSize(100);
	}

}
