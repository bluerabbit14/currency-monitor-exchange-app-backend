package com.example.currency.rate.scheduler;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.service.GoldApiCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GoldApiRateSchedulerTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(GoldApiCollectionService.class, () -> mock(GoldApiCollectionService.class))
			.withUserConfiguration(GoldApiRateScheduler.class);

	@Test
	void isNotRegisteredUnlessEnabled() {
		runner.run(context -> assertThat(context).doesNotHaveBean(GoldApiRateScheduler.class));
		runner.withPropertyValues("app.rates.goldapi.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(GoldApiRateScheduler.class));
	}

	@Test
	void isRegisteredWhenEnabledWithoutAnyApiKey() {
		runner.withPropertyValues("app.rates.goldapi.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(GoldApiRateScheduler.class));
	}

	@Test
	void otherSchedulersSwitchesDoNotEnableIt() {
		runner.withPropertyValues("app.rates.collection.enabled=true", "app.rates.coingecko.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean(GoldApiRateScheduler.class));
	}

	@Test
	void defaultsToTenMinutesAndIsConfigurable() throws Exception {
		Scheduled scheduled = GoldApiRateScheduler.class.getMethod("collectRates").getAnnotation(Scheduled.class);

		assertEquals("${app.rates.goldapi.interval:PT10M}", scheduled.fixedDelayString());
		assertEquals("${app.rates.goldapi.initial-delay:PT60S}", scheduled.initialDelayString());
		assertEquals(-1L, scheduled.fixedRate());
		RateProperties defaults = new RateProperties();
		assertEquals(Duration.ofMinutes(10), defaults.getGoldapi().getInterval());
		assertFalse(defaults.getGoldapi().isEnabled());
		assertTrue(defaults.getGoldapi().getRequestsPerMinute() < 10, "default must stay below the 10/min limit");
		assertEquals(10, defaults.getGoldapi().getRequestsPerHour());
	}

	@Test
	void shippedPropertiesUseTenMinutesAndAReducedRequestRate() throws IOException {
		String properties = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.properties"));
		assertTrue(properties.contains("app.rates.goldapi.interval=PT10M"));
		assertTrue(properties.contains("app.rates.goldapi.requests-per-minute=6"));
		assertTrue(properties.contains("app.rates.goldapi.requests-per-hour=10"));
		assertTrue(properties.contains("app.rates.goldapi.api-key=${GOLDAPI_API_KEY:}"));
		assertTrue(properties.contains("app.rates.goldapi.enabled=${GOLDAPI_COLLECTION_ENABLED:false}"));
	}

	@Test
	void aFailingRunDoesNotEscapeToTheScheduler() {
		GoldApiCollectionService service = mock(GoldApiCollectionService.class);
		doThrow(new IllegalStateException("boom")).when(service).collect();

		new GoldApiRateScheduler(service).collectRates();

		verify(service).collect();
	}

}
