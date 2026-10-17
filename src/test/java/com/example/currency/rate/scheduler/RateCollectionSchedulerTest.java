package com.example.currency.rate.scheduler;

import com.example.currency.rate.service.RateCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateCollectionSchedulerTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(RateCollectionService.class, () -> mock(RateCollectionService.class))
			.withUserConfiguration(RateCollectionScheduler.class);

	@Test
	void isNotRegisteredUnlessEnabled() {
		runner.run(context -> org.assertj.core.api.Assertions
				.assertThat(context).doesNotHaveBean(RateCollectionScheduler.class));
		runner.withPropertyValues("app.rates.collection.enabled=false").run(context -> org.assertj.core.api.Assertions
				.assertThat(context).doesNotHaveBean(RateCollectionScheduler.class));
	}

	@Test
	void isRegisteredWhenEnabled() {
		runner.withPropertyValues("app.rates.collection.enabled=true").run(context -> org.assertj.core.api.Assertions
				.assertThat(context).hasSingleBean(RateCollectionScheduler.class));
	}

	@Test
	void intervalAndInitialDelayComeFromConfiguration() throws Exception {
		Method method = RateCollectionScheduler.class.getMethod("collectRates");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);

		assertEquals("${app.rates.collection.interval:PT12M}", scheduled.fixedDelayString());
		assertEquals("${app.rates.collection.initial-delay:PT30S}", scheduled.initialDelayString());
		assertEquals(-1L, scheduled.fixedRate());
	}

	@Test
	void aFailingRunDoesNotEscapeToTheScheduler() {
		RateCollectionService service = mock(RateCollectionService.class);
		doThrow(new IllegalStateException("boom")).when(service).collect();

		new RateCollectionScheduler(service).collectRates();

		verify(service).collect();
	}

}
