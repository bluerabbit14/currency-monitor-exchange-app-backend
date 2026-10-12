package com.example.currency.asset.repository;

import com.example.currency.asset.entity.Asset;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AssetRepositoryIT {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	@Autowired
	private AssetRepository assetRepository;

	@Test
	void seedsTheSupportedCatalog() {
		List<Asset> assets = active(null, null);

		assertEquals(11, assets.size());
		assertEquals(List.of(
				"BNB",
				"Bitcoin",
				"British Pound",
				"Ethereum",
				"Euro",
				"Gold",
				"Indian Rupee",
				"Japanese Yen",
				"Silver",
				"Solana",
				"US Dollar"), assets.stream().map(Asset::getName).toList());
		Asset usd = assets.stream().filter(asset -> asset.getSymbol().equals("USD")).findFirst().orElseThrow();
		assertNull(usd.getProvider());
		assertNull(usd.getExternalId());
		assertEquals(AssetStatus.ACTIVE, usd.getStatus());
	}

	@Test
	void filtersEachCategory() {
		assertEquals(List.of("GBP", "EUR", "INR", "JPY", "USD"), symbols(AssetType.FIAT, null));
		assertEquals(List.of("BNB", "BTC", "ETH", "SOL"), symbols(AssetType.CRYPTO, null));
		assertEquals(List.of("GOLD", "SILVER"), symbols(AssetType.COMMODITY, null));
	}

	@Test
	void searchesSymbolAndNameIgnoringCase() {
		assertEquals(List.of("USD"), symbols(null, "usd"));
		assertEquals(List.of("BTC"), symbols(null, "bitcoin"));
		assertEquals(List.of("BTC", "GBP"), symbols(null, "BIT"));
	}

	@Test
	void combinesCategoryAndSearch() {
		assertEquals(List.of("BTC"), symbols(AssetType.CRYPTO, "bit"));
	}

	@Test
	void excludesInactiveAssets() {
		Asset inactive = crypto("DOGE", "Dogecoin", AssetStatus.INACTIVE, null, null);
		assetRepository.saveAndFlush(inactive);

		assertEquals(11, active(null, null).size());
		assertTrue(symbols(null, null).stream().noneMatch("DOGE"::equals));
		assertTrue(assetRepository.findByIdAndStatus(inactive.getId(), AssetStatus.ACTIVE).isEmpty());
		assertTrue(assetRepository.findByIdAndStatus(UUID.randomUUID(), AssetStatus.ACTIVE).isEmpty());
	}

	@Test
	void allowsManyUnmappedAssetsAndRejectsDuplicateProviderIds() {
		assetRepository.saveAndFlush(crypto("XBT", "Bitcoin Cash", AssetStatus.ACTIVE, null, null));
		assetRepository.saveAndFlush(crypto("XBT2", "Wrapped", AssetStatus.ACTIVE, "coingecko", "wrapped-bitcoin"));

		assertThrows(DataIntegrityViolationException.class, () -> assetRepository.saveAndFlush(
				crypto("XBT3", "Duplicate", AssetStatus.ACTIVE, "coingecko", "wrapped-bitcoin")));
	}

	private List<Asset> active(AssetType type, String search) {
		return assetRepository.findActive(type, search, PageRequest.of(0, 20, Sort.by("name"))).getContent();
	}

	private List<String> symbols(AssetType type, String search) {
		return active(type, search).stream().map(Asset::getSymbol).toList();
	}

	private static Asset crypto(String symbol, String name, AssetStatus status, String provider, String externalId) {
		Asset asset = new Asset();
		asset.setSymbol(symbol);
		asset.setName(name);
		asset.setAssetType(AssetType.CRYPTO);
		asset.setDecimalScale((short) 8);
		asset.setStatus(status);
		asset.setProvider(provider);
		asset.setExternalId(externalId);
		return asset;
	}

}
