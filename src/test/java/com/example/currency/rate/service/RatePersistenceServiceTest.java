package com.example.currency.rate.service;

import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.dto.RateObservation;
import com.example.currency.rate.entity.ExchangeRate;
import com.example.currency.rate.entity.RateHistory;
import com.example.currency.rate.exception.InvalidRateObservationException;
import com.example.currency.rate.repository.ExchangeRateRepository;
import com.example.currency.rate.repository.RateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatePersistenceServiceTest {

	@Mock
	private ExchangeRateRepository exchangeRateRepository;

	@Mock
	private RateHistoryRepository rateHistoryRepository;

	@Mock
	private AssetPairRepository assetPairRepository;

	private RatePersistenceService ratePersistenceService;

	@BeforeEach
	void setUp() {
		ratePersistenceService = new RatePersistenceService(
				exchangeRateRepository,
				rateHistoryRepository,
				assetPairRepository);
	}

	@Test
	void recordsHistoryAndCreatesCurrentRate() {
		UUID pairId = UUID.randomUUID();
		AssetPair pair = pair(pairId);
		when(assetPairRepository.findById(pairId)).thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findById(pairId)).thenReturn(Optional.empty());

		Instant observedAt = Instant.parse("2026-10-12T10:00:00Z");
		RateObservation observation = new RateObservation(
				pairId,
				new BigDecimal("83.25"),
				observedAt,
				observedAt.plusSeconds(2),
				"provider-a",
				"obs-1");

		boolean changed = ratePersistenceService.recordObservation(observation);

		assertTrue(changed);
		verify(rateHistoryRepository).save(any(RateHistory.class));
		ArgumentCaptor<ExchangeRate> rateCaptor = ArgumentCaptor.forClass(ExchangeRate.class);
		verify(exchangeRateRepository).save(rateCaptor.capture());
		assertEquals(new BigDecimal("83.25"), rateCaptor.getValue().getRate());
		assertEquals(observedAt, rateCaptor.getValue().getObservedAt());
	}

	@Test
	void rejectsNonPositiveRates() {
		RateObservation observation = new RateObservation(
				UUID.randomUUID(),
				BigDecimal.ZERO,
				Instant.parse("2026-10-12T10:00:00Z"),
				Instant.parse("2026-10-12T10:00:01Z"),
				"provider-a",
				null);

		assertThrows(InvalidRateObservationException.class, () -> ratePersistenceService.recordObservation(observation));
		verify(assetPairRepository, never()).findById(any());
	}

	@Test
	void doesNotOverwriteNewerCurrentRateWithOlderObservation() {
		UUID pairId = UUID.randomUUID();
		AssetPair pair = pair(pairId);
		ExchangeRate current = new ExchangeRate();
		current.setPair(pair);
		current.setRate(new BigDecimal("84"));
		current.setObservedAt(Instant.parse("2026-10-12T12:00:00Z"));
		current.setFetchedAt(Instant.parse("2026-10-12T12:00:01Z"));
		current.setProviderCode("provider-a");

		when(assetPairRepository.findById(pairId)).thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findById(pairId)).thenReturn(Optional.of(current));

		RateObservation older = new RateObservation(
				pairId,
				new BigDecimal("80"),
				Instant.parse("2026-10-12T11:00:00Z"),
				Instant.parse("2026-10-12T11:00:01Z"),
				"provider-a",
				"obs-old");

		boolean changed = ratePersistenceService.recordObservation(older);

		assertTrue(changed);
		verify(rateHistoryRepository).save(any(RateHistory.class));
		verify(exchangeRateRepository, never()).save(any());
		assertEquals(new BigDecimal("84"), current.getRate());
	}

	@Test
	void updatesCurrentRateWhenObservationIsNewer() {
		UUID pairId = UUID.randomUUID();
		AssetPair pair = pair(pairId);
		ExchangeRate current = new ExchangeRate();
		current.setPair(pair);
		current.setRate(new BigDecimal("84"));
		current.setObservedAt(Instant.parse("2026-10-12T11:00:00Z"));
		current.setFetchedAt(Instant.parse("2026-10-12T11:00:01Z"));
		current.setProviderCode("provider-a");

		when(assetPairRepository.findById(pairId)).thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findById(pairId)).thenReturn(Optional.of(current));

		RateObservation newer = new RateObservation(
				pairId,
				new BigDecimal("85"),
				Instant.parse("2026-10-12T12:00:00Z"),
				Instant.parse("2026-10-12T12:00:01Z"),
				"provider-a",
				"obs-new");

		boolean changed = ratePersistenceService.recordObservation(newer);

		assertTrue(changed);
		verify(exchangeRateRepository).save(current);
		assertEquals(new BigDecimal("85"), current.getRate());
		assertEquals(Instant.parse("2026-10-12T12:00:00Z"), current.getObservedAt());
	}

	@Test
	void skipsDuplicateProviderObservationIds() {
		UUID pairId = UUID.randomUUID();
		AssetPair pair = pair(pairId);
		ExchangeRate current = new ExchangeRate();
		current.setPair(pair);
		current.setRate(new BigDecimal("84"));
		current.setObservedAt(Instant.parse("2026-10-12T12:00:00Z"));
		current.setFetchedAt(Instant.parse("2026-10-12T12:00:01Z"));
		current.setProviderCode("provider-a");

		when(assetPairRepository.findById(pairId)).thenReturn(Optional.of(pair));
		when(rateHistoryRepository.findByPairIdAndProviderCodeAndProviderObservationId(
				pairId,
				"provider-a",
				"obs-1")).thenReturn(Optional.of(new RateHistory()));
		when(exchangeRateRepository.findById(pairId)).thenReturn(Optional.of(current));

		RateObservation duplicate = new RateObservation(
				pairId,
				new BigDecimal("84"),
				Instant.parse("2026-10-12T11:00:00Z"),
				Instant.parse("2026-10-12T11:00:01Z"),
				"provider-a",
				"obs-1");

		boolean changed = ratePersistenceService.recordObservation(duplicate);

		assertFalse(changed);
		verify(rateHistoryRepository, never()).save(any());
		verify(exchangeRateRepository, never()).save(any());
	}

	@Test
	void keepsCurrentAndHistoryConsistentOnFirstObservation() {
		UUID pairId = UUID.randomUUID();
		AssetPair pair = pair(pairId);
		when(assetPairRepository.findById(pairId)).thenReturn(Optional.of(pair));
		when(exchangeRateRepository.findById(pairId)).thenReturn(Optional.empty());

		Instant observedAt = Instant.parse("2026-10-12T10:00:00Z");
		Instant fetchedAt = Instant.parse("2026-10-12T10:00:02Z");
		ratePersistenceService.recordObservation(new RateObservation(
				pairId,
				new BigDecimal("1.23"),
				observedAt,
				fetchedAt,
				"provider-a",
				null));

		ArgumentCaptor<RateHistory> historyCaptor = ArgumentCaptor.forClass(RateHistory.class);
		ArgumentCaptor<ExchangeRate> rateCaptor = ArgumentCaptor.forClass(ExchangeRate.class);
		verify(rateHistoryRepository).save(historyCaptor.capture());
		verify(exchangeRateRepository).save(rateCaptor.capture());
		assertEquals(historyCaptor.getValue().getRate(), rateCaptor.getValue().getRate());
		assertEquals(historyCaptor.getValue().getObservedAt(), rateCaptor.getValue().getObservedAt());
		assertEquals(historyCaptor.getValue().getProviderCode(), rateCaptor.getValue().getProviderCode());
	}

	private static AssetPair pair(UUID id) {
		AssetPair pair = new AssetPair();
		setField(pair, "id", id);
		return pair;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> type = target.getClass();
		while (type != null) {
			try {
				var field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(target, value);
				return;
			}
			catch (NoSuchFieldException ex) {
				type = type.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new IllegalStateException(name);
	}

}
