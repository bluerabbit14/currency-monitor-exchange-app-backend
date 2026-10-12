package com.example.currency.rate.repository;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RateRepositoryIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private AssetPairRepository assetPairRepository;

	@Autowired
	private ExchangeRateRepository exchangeRateRepository;

	@Autowired
	private RateHistoryRepository rateHistoryRepository;

	@Test
	void seedsConfiguredPairsWithoutRates() {
		assertEquals(8, assetPairRepository.count());
		assertEquals(0, exchangeRateRepository.count());
		assertEquals(0, rateHistoryRepository.count());
		assertTrue(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "BTC", "USD").isPresent());
		assertTrue(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR").isPresent());
	}

	@Test
	void rejectsSelfPairAndDuplicateBaseQuote() {
		Asset usd = assetRepository.findActive(AssetType.FIAT, "USD", PageRequest.of(0, 1)).getContent().getFirst();

		AssetPair self = new AssetPair();
		self.setBaseAsset(usd);
		self.setQuoteAsset(usd);
		self.setSymbol("USD/USD");
		self.setStatus(AssetStatus.ACTIVE);
		assertThrows(DataIntegrityViolationException.class, () -> assetPairRepository.saveAndFlush(self));

		AssetPair duplicate = new AssetPair();
		duplicate.setBaseAsset(usd);
		Asset inr = assetRepository.findActive(AssetType.FIAT, "INR", PageRequest.of(0, 1)).getContent().getFirst();
		duplicate.setQuoteAsset(inr);
		duplicate.setSymbol("USD-INR");
		duplicate.setStatus(AssetStatus.ACTIVE);
		assertThrows(DataIntegrityViolationException.class, () -> assetPairRepository.saveAndFlush(duplicate));
	}

	@Test
	void filtersCurrentRatesInDatabase() {
		AssetPair btcUsd = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "BTC", "USD").orElseThrow();
		AssetPair usdInr = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR").orElseThrow();
		saveRate(btcUsd, "65000");
		saveRate(usdInr, "83");

		Page<ExchangeRate> cryptoUsd = exchangeRateRepository.findCurrentRates(
				AssetStatus.ACTIVE,
				AssetType.CRYPTO,
				null,
				"USD",
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "observedAt")));

		assertEquals(1, cryptoUsd.getTotalElements());
		assertEquals("BTC", cryptoUsd.getContent().getFirst().getPair().getBaseAsset().getSymbol());
	}

	@Test
	void rejectsNonPositiveCurrentRate() {
		AssetPair pair = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "ETH", "USD").orElseThrow();
		ExchangeRate rate = new ExchangeRate();
		rate.setPair(pair);
		rate.setRate(BigDecimal.ZERO);
		rate.setObservedAt(Instant.parse("2026-10-12T10:00:00Z"));
		rate.setFetchedAt(Instant.parse("2026-10-12T10:00:01Z"));
		rate.setProviderCode("test");

		assertThrows(DataIntegrityViolationException.class, () -> exchangeRateRepository.saveAndFlush(rate));
	}

	@Test
	void enforcesProviderObservationDedupAndHistoryQuery() {
		AssetPair pair = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "GOLD", "USD").orElseThrow();
		Instant first = Instant.parse("2026-10-10T10:00:00Z");
		Instant second = Instant.parse("2026-10-11T10:00:00Z");
		saveHistory(pair, "2400", first, "obs-1");
		saveHistory(pair, "2410", second, "obs-2");

		RateHistory duplicate = new RateHistory();
		duplicate.setPair(pair);
		duplicate.setRate(new BigDecimal("2399"));
		duplicate.setObservedAt(Instant.parse("2026-10-12T10:00:00Z"));
		duplicate.setFetchedAt(Instant.parse("2026-10-12T10:00:01Z"));
		duplicate.setProviderCode("test");
		duplicate.setProviderObservationId("obs-1");
		assertThrows(DataIntegrityViolationException.class, () -> rateHistoryRepository.saveAndFlush(duplicate));

		Page<RateHistory> page = rateHistoryRepository.findHistory(
				pair.getId(),
				Instant.parse("2026-10-10T00:00:00Z"),
				Instant.parse("2026-10-12T00:00:00Z"),
				PageRequest.of(0, 10, Sort.by(Sort.Order.desc("observedAt"), Sort.Order.desc("id"))));

		assertEquals(2, page.getTotalElements());
		assertEquals(List.of(second, first), page.getContent().stream().map(RateHistory::getObservedAt).toList());
	}

	@Test
	void excludesInactivePairsFromCurrentRateQueries() {
		AssetPair pair = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "SOL", "USD").orElseThrow();
		saveRate(pair, "150");
		pair.setStatus(AssetStatus.INACTIVE);
		assetPairRepository.saveAndFlush(pair);

		Page<ExchangeRate> page = exchangeRateRepository.findCurrentRates(
				AssetStatus.ACTIVE,
				AssetType.CRYPTO,
				"SOL",
				"USD",
				PageRequest.of(0, 20));

		assertEquals(0, page.getTotalElements());
	}

	private void saveRate(AssetPair pair, String value) {
		ExchangeRate rate = new ExchangeRate();
		rate.setPair(pair);
		rate.setRate(new BigDecimal(value));
		rate.setObservedAt(Instant.parse("2026-10-12T10:00:00Z"));
		rate.setFetchedAt(Instant.parse("2026-10-12T10:00:01Z"));
		rate.setProviderCode("test");
		exchangeRateRepository.saveAndFlush(rate);
	}

	private void saveHistory(AssetPair pair, String value, Instant observedAt, String observationId) {
		RateHistory history = new RateHistory();
		history.setPair(pair);
		history.setRate(new BigDecimal(value));
		history.setObservedAt(observedAt);
		history.setFetchedAt(observedAt.plusSeconds(1));
		history.setProviderCode("test");
		history.setProviderObservationId(observationId);
		rateHistoryRepository.saveAndFlush(history);
	}

}
