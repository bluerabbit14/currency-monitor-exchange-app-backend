package com.example.currency.asset.service;

import com.example.currency.asset.dto.CurrencyItemResponse;
import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.repository.AssetRepository;
import com.example.currency.favorite.repository.FavoriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurrencyCatalogServiceTest {

	private final AssetRepository assetRepository = mock(AssetRepository.class);
	private final FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
	private final CurrencyCatalogService service = new CurrencyCatalogService(assetRepository, favoriteRepository);

	private static Asset asset(String symbol, String name, AssetType type) {
		Asset asset = new Asset();
		ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(type);
		asset.setStatus(AssetStatus.ACTIVE);
		asset.setDecimalScale((short) 2);
		return asset;
	}

	@Test
	void groupsByTypeAndFlagsFavorites() {
		Asset usd = asset("USD", "US Dollar", AssetType.FIAT);
		Asset btc = asset("BTC", "Bitcoin", AssetType.CRYPTO);
		UUID userId = UUID.randomUUID();
		when(assetRepository.findByStatusOrderByNameAsc(AssetStatus.ACTIVE)).thenReturn(List.of(btc, usd));
		when(favoriteRepository.findAssetIdsByUserId(userId)).thenReturn(List.of(usd.getId()));

		Map<AssetType, List<CurrencyItemResponse>> grouped = service.listGroupedByType(userId);

		assertTrue(grouped.get(AssetType.FIAT).get(0).favorite());
		assertFalse(grouped.get(AssetType.CRYPTO).get(0).favorite());
		assertEquals(0, grouped.get(AssetType.COMMODITY).size());
	}

	@Test
	void anonymousCallersSkipTheFavoriteLookup() {
		Asset usd = asset("USD", "US Dollar", AssetType.FIAT);
		when(assetRepository.findByStatusOrderByNameAsc(AssetStatus.ACTIVE)).thenReturn(List.of(usd));

		Map<AssetType, List<CurrencyItemResponse>> grouped = service.listGroupedByType(null);

		assertFalse(grouped.get(AssetType.FIAT).get(0).favorite());
		verifyNoInteractions(favoriteRepository);
	}

}
