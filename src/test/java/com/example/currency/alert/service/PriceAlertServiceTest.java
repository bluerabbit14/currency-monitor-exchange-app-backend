package com.example.currency.alert.service;

import com.example.currency.alert.dto.CreatePriceAlertRequest;
import com.example.currency.alert.dto.PriceAlertResponse;
import com.example.currency.alert.dto.UpdatePriceAlertRequest;
import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.alert.enums.AlertCondition;
import com.example.currency.alert.enums.AlertStatus;
import com.example.currency.alert.exception.AlertNotFoundException;
import com.example.currency.alert.repository.PriceAlertRepository;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.user.entity.User;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

	@Mock
	private PriceAlertRepository priceAlertRepository;

	@Mock
	private AssetPairRepository assetPairRepository;

	@Mock
	private UserRepository userRepository;

	private PriceAlertService priceAlertService;

	@BeforeEach
	void setUp() {
		priceAlertService = new PriceAlertService(priceAlertRepository, assetPairRepository, userRepository);
	}

	@Test
	void createsAlertForActiveConfiguredPair() {
		UUID userId = UUID.randomUUID();
		User user = userReference(userId);
		AssetPair pair = usdInrPair();
		PriceAlert saved = alert(user, pair, AlertCondition.ABOVE, "90.00", AlertStatus.ACTIVE,
				Instant.parse("2026-10-12T04:00:00Z"));

		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "USD", "INR"))
				.thenReturn(Optional.of(pair));
		when(userRepository.getReferenceById(userId)).thenReturn(user);
		when(priceAlertRepository.save(any(PriceAlert.class))).thenReturn(saved);

		PriceAlertResponse response = priceAlertService.create(userId, new CreatePriceAlertRequest(
				"usd", "inr", AlertCondition.ABOVE, new BigDecimal("90.00")));

		assertEquals(saved.getId(), response.id());
		assertEquals("USD", response.base());
		assertEquals("INR", response.quote());
		assertEquals(AlertCondition.ABOVE, response.condition());
		assertEquals(new BigDecimal("90.00"), response.targetRate());
		assertEquals(AlertStatus.ACTIVE, response.status());
		assertNull(response.triggeredAt());

		ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
		verify(priceAlertRepository).save(captor.capture());
		assertEquals(AlertStatus.ACTIVE, captor.getValue().getStatus());
		assertNull(captor.getValue().getTriggeredAt());
		assertEquals(new BigDecimal("90.00"), captor.getValue().getTargetPrice());
	}

	@Test
	void rejectsUnknownOrInactivePair() {
		UUID userId = UUID.randomUUID();
		when(assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, "AAA", "BBB"))
				.thenReturn(Optional.empty());

		assertThrows(UnsupportedPairException.class, () -> priceAlertService.create(userId,
				new CreatePriceAlertRequest("AAA", "BBB", AlertCondition.BELOW, new BigDecimal("1"))));
		verify(priceAlertRepository, never()).save(any());
	}

	@Test
	void rejectsZeroAndNegativeTargetRates() {
		UUID userId = UUID.randomUUID();

		assertThrows(IllegalArgumentException.class, () -> priceAlertService.create(userId,
				new CreatePriceAlertRequest("USD", "INR", AlertCondition.ABOVE, BigDecimal.ZERO)));
		assertThrows(IllegalArgumentException.class, () -> priceAlertService.create(userId,
				new CreatePriceAlertRequest("USD", "INR", AlertCondition.ABOVE, new BigDecimal("-1"))));
		verify(assetPairRepository, never()).findByStatusAndSymbols(any(), any(), any());
	}

	@Test
	void listsOnlyAuthenticatedUserAlerts() {
		UUID userId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
		PriceAlert alert = alert(userReference(userId), usdInrPair(), AlertCondition.ABOVE, "90",
				AlertStatus.ACTIVE, Instant.parse("2026-10-12T04:00:00Z"));
		when(priceAlertRepository.findByUserId(userId, pageable))
				.thenReturn(new PageImpl<>(List.of(alert), pageable, 1));

		Page<PriceAlertResponse> page = priceAlertService.list(userId, pageable);

		assertEquals(1, page.getTotalElements());
		assertEquals(alert.getId(), page.getContent().getFirst().id());
		verify(priceAlertRepository).findByUserId(userId, pageable);
	}

	@Test
	void retrievesOwnedAlert() {
		UUID userId = UUID.randomUUID();
		PriceAlert alert = alert(userReference(userId), usdInrPair(), AlertCondition.BELOW, "80",
				AlertStatus.ACTIVE, Instant.parse("2026-10-12T04:00:00Z"));
		when(priceAlertRepository.findByIdAndUserId(alert.getId(), userId)).thenReturn(Optional.of(alert));

		PriceAlertResponse response = priceAlertService.get(userId, alert.getId());

		assertEquals(alert.getId(), response.id());
		assertEquals(AlertCondition.BELOW, response.condition());
	}

	@Test
	void preventsAccessToAnotherUsersAlert() {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(priceAlertRepository.findByIdAndUserId(alertId, userId)).thenReturn(Optional.empty());

		assertThrows(AlertNotFoundException.class, () -> priceAlertService.get(userId, alertId));
		assertThrows(AlertNotFoundException.class, () -> priceAlertService.update(userId, alertId,
				new UpdatePriceAlertRequest(AlertCondition.BELOW, null)));
		when(priceAlertRepository.deleteByIdAndUserId(alertId, userId)).thenReturn(0);
		assertThrows(AlertNotFoundException.class, () -> priceAlertService.delete(userId, alertId));
	}

	@Test
	void updatesOnlySuppliedFieldsAndPreservesStatus() {
		UUID userId = UUID.randomUUID();
		PriceAlert alert = alert(userReference(userId), usdInrPair(), AlertCondition.ABOVE, "90.00",
				AlertStatus.TRIGGERED, Instant.parse("2026-10-12T04:00:00Z"));
		alert.setTriggeredAt(Instant.parse("2026-10-12T05:00:00Z"));
		when(priceAlertRepository.findByIdAndUserId(alert.getId(), userId)).thenReturn(Optional.of(alert));

		PriceAlertResponse response = priceAlertService.update(userId, alert.getId(),
				new UpdatePriceAlertRequest(AlertCondition.BELOW, new BigDecimal("89.50")));

		assertEquals(AlertCondition.BELOW, response.condition());
		assertEquals(new BigDecimal("89.50"), response.targetRate());
		assertEquals(AlertStatus.TRIGGERED, response.status());
		assertEquals(Instant.parse("2026-10-12T05:00:00Z"), response.triggeredAt());
		assertEquals(AlertStatus.TRIGGERED, alert.getStatus());
	}

	@Test
	void updateWithOnlyConditionLeavesTargetRateUnchanged() {
		UUID userId = UUID.randomUUID();
		PriceAlert alert = alert(userReference(userId), usdInrPair(), AlertCondition.ABOVE, "90.00",
				AlertStatus.CANCELLED, Instant.parse("2026-10-12T04:00:00Z"));
		when(priceAlertRepository.findByIdAndUserId(alert.getId(), userId)).thenReturn(Optional.of(alert));

		PriceAlertResponse response = priceAlertService.update(userId, alert.getId(),
				new UpdatePriceAlertRequest(AlertCondition.BELOW, null));

		assertEquals(AlertCondition.BELOW, response.condition());
		assertEquals(new BigDecimal("90.00"), response.targetRate());
		assertEquals(AlertStatus.CANCELLED, response.status());
	}

	@Test
	void deletesOwnedAlert() {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(priceAlertRepository.deleteByIdAndUserId(alertId, userId)).thenReturn(1);

		priceAlertService.delete(userId, alertId);

		verify(priceAlertRepository).deleteByIdAndUserId(eq(alertId), eq(userId));
	}

	private static AssetPair usdInrPair() {
		Asset usd = asset("USD", "US Dollar", AssetType.FIAT);
		Asset inr = asset("INR", "Indian Rupee", AssetType.FIAT);
		AssetPair pair = new AssetPair();
		setField(pair, "id", UUID.randomUUID());
		pair.setBaseAsset(usd);
		pair.setQuoteAsset(inr);
		pair.setSymbol("USD/INR");
		pair.setStatus(AssetStatus.ACTIVE);
		return pair;
	}

	private static Asset asset(String symbol, String name, AssetType type) {
		Asset asset = new Asset();
		setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(type);
		asset.setDecimalScale((short) 2);
		asset.setStatus(AssetStatus.ACTIVE);
		return asset;
	}

	private static User userReference(UUID userId) {
		User user = new User();
		setField(user, "id", userId);
		return user;
	}

	private static PriceAlert alert(
			User user,
			AssetPair pair,
			AlertCondition condition,
			String target,
			AlertStatus status,
			Instant createdAt) {
		PriceAlert alert = new PriceAlert();
		setField(alert, "id", UUID.randomUUID());
		setField(alert, "createdAt", createdAt);
		setField(alert, "updatedAt", createdAt);
		alert.setUser(user);
		alert.setPair(pair);
		alert.setCondition(condition);
		alert.setTargetPrice(new BigDecimal(target));
		alert.setStatus(status);
		return alert;
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
