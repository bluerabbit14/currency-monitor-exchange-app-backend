package com.example.currency.rate.scheduler;

import com.example.currency.rate.config.RateProperties;
import com.example.currency.rate.service.CoinGeckoCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CoinGeckoRateSchedulerTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(CoinGeckoCollectionService.class, () -> mock(CoinGeckoCollectionService.class))
			.withUserConfiguration(CoinGeckoRateScheduler.class);

	@Test
	void isNotRegisteredUnlessEnabled() {
		runner.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
		runner.withPropertyValues("app.rates.coingecko.enabled=false", "app.rates.coingecko.api-key=k")
				.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
	}

	@Test
	void isNotRegisteredWithoutAnApiKey() {
		runner.withPropertyValues("app.rates.coingecko.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
		runner.withPropertyValues("app.rates.coingecko.enabled=true", "app.rates.coingecko.api-key=")
				.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
		runner.withPropertyValues("app.rates.coingecko.enabled=true", "app.rates.coingecko.api-key=  ")
				.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
	}

	@Test
	void isRegisteredWhenEnabledWithAnApiKey() {
		runner.withPropertyValues("app.rates.coingecko.enabled=true", "app.rates.coingecko.api-key=k")
				.run(context -> assertThat(context).hasSingleBean(CoinGeckoRateScheduler.class));
	}

	@Test
	void doesNotShareASwitchWithTheTwelveDataScheduler() {
		runner.withPropertyValues("app.rates.collection.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean(CoinGeckoRateScheduler.class));
	}

	@Test
	void defaultsToFiveMinutesAndIsConfigurable() throws Exception {
		Scheduled scheduled = CoinGeckoRateScheduler.class.getMethod("collectRates").getAnnotation(Scheduled.class);

		assertEquals("${app.rates.coingecko.interval:PT5M}", scheduled.fixedDelayString());
		assertEquals("${app.rates.coingecko.initial-delay:PT45S}", scheduled.initialDelayString());
		assertEquals(-1L, scheduled.fixedRate());
		assertEquals(Duration.ofMinutes(5), new RateProperties().getCoingecko().getInterval());
		assertEquals(9000, new RateProperties().getCoingecko().getMonthlyRequestLimit());
	}

	@Test
	void usesItsOwnMethodNotTheTwelveDataOne() throws Exception {
		Method method = CoinGeckoRateScheduler.class.getMethod("collectRates");

		assertThat(method.getDeclaringClass()).isNotEqualTo(RateCollectionScheduler.class);
	}

	@Test
	void aFailingRunDoesNotEscapeToTheScheduler() {
		CoinGeckoCollectionService service = mock(CoinGeckoCollectionService.class);
		doThrow(new IllegalStateException("boom")).when(service).collect();

		new CoinGeckoRateScheduler(service).collectRates();

		verify(service).collect();
	}

}
