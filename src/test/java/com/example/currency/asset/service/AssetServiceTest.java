package com.example.currency.asset.service;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.asset.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

	@Mock
	private AssetRepository assetRepository;

	private AssetService assetService;

	@BeforeEach
	void setUp() {
		assetService = new AssetService(assetRepository);
	}

	@Test
	void listsActiveAssets() {
		Asset usd = asset("USD", "US Dollar", AssetType.FIAT, AssetStatus.ACTIVE);
		Pageable pageable = PageRequest.of(0, 20);
		when(assetRepository.findActive(null, null, pageable)).thenReturn(new PageImpl<>(List.of(usd), pageable, 1));

		Page<AssetResponse> page = assetService.listActive(null, null, pageable);

		assertEquals(1, page.getTotalElements());
		assertEquals(new AssetResponse(usd.getId(), "USD", "US Dollar", AssetType.FIAT, AssetStatus.ACTIVE),
				page.getContent().get(0));
	}

	@ParameterizedTest
	@EnumSource(AssetType.class)
	void filtersByCategory(AssetType type) {
		Pageable pageable = PageRequest.of(0, 20);
		when(assetRepository.findActive(type, null, pageable)).thenReturn(Page.empty(pageable));

		Page<AssetResponse> page = assetService.listActive(type, "   ", pageable);

		assertEquals(0, page.getTotalElements());
		verify(assetRepository).findActive(type, null, pageable);
	}

	@Test
	void searchesBySymbol() {
		Pageable pageable = PageRequest.of(0, 20);
		when(assetRepository.findActive(null, "usd", pageable)).thenReturn(Page.empty(pageable));

		assetService.listActive(null, "usd", pageable);

		verify(assetRepository).findActive(null, "usd", pageable);
	}

	@Test
	void searchesByName() {
		Pageable pageable = PageRequest.of(0, 20);
		when(assetRepository.findActive(null, "bitcoin", pageable)).thenReturn(Page.empty(pageable));

		assetService.listActive(null, "bitcoin", pageable);

		verify(assetRepository).findActive(null, "bitcoin", pageable);
	}

	@Test
	void combinesCategoryAndSearch() {
		Pageable pageable = PageRequest.of(0, 20);
		when(assetRepository.findActive(AssetType.CRYPTO, "bit", pageable)).thenReturn(Page.empty(pageable));

		assetService.listActive(AssetType.CRYPTO, "bit", pageable);

		verify(assetRepository).findActive(AssetType.CRYPTO, "bit", pageable);
	}

	@Test
	void escapesLikeWildcards() {
		assertNull(AssetService.normalizeSearch("   "));
		assertEquals("100\\%", AssetService.normalizeSearch("100%"));
		assertEquals("a\\_b", AssetService.normalizeSearch("a_b"));
		assertEquals("\\\\path", AssetService.normalizeSearch("\\path"));
	}

	@Test
	void missingAssetIsNotFound() {
		UUID id = UUID.randomUUID();
		when(assetRepository.findByIdAndStatus(id, AssetStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThrows(AssetNotFoundException.class, () -> assetService.getActive(id));
	}

	@Test
	void inactiveAssetIsNotFound() {
		UUID id = UUID.randomUUID();
		when(assetRepository.findByIdAndStatus(id, AssetStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThrows(AssetNotFoundException.class, () -> assetService.getActive(id));
		verify(assetRepository).findByIdAndStatus(id, AssetStatus.ACTIVE);
	}

	@Test
	void listsActiveFiatCurrenciesOnlyByName() {
		Asset eur = asset("EUR", "Euro", AssetType.FIAT, AssetStatus.ACTIVE);
		when(assetRepository.findByAssetTypeAndStatusOrderByNameAsc(AssetType.FIAT, AssetStatus.ACTIVE))
				.thenReturn(List.of(eur));

		List<AssetResponse> currencies = assetService.listActiveCurrencies();

		assertEquals(1, currencies.size());
		assertEquals("EUR", currencies.get(0).symbol());
	}

	@Test
	void findActiveCurrencyLooksUpFiatOnly() {
		UUID id = UUID.randomUUID();
		when(assetRepository.findByIdAndAssetTypeAndStatus(id, AssetType.FIAT, AssetStatus.ACTIVE))
				.thenReturn(Optional.empty());

		assertEquals(Optional.empty(), assetService.findActiveCurrency(id));
	}

	private static Asset asset(String symbol, String name, AssetType type, AssetStatus status) {
		Asset asset = new Asset();
		setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(type);
		asset.setStatus(status);
		asset.setDecimalScale((short) 2);
		return asset;
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
