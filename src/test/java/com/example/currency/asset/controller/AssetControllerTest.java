package com.example.currency.asset.controller;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.asset.service.AssetService;
import com.example.currency.config.WebDataConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssetController.class, properties = "spring.data.web.pageable.max-page-size=100")
@Import(WebDataConfig.class)
class AssetControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AssetService assetService;

	@Test
	void listsActiveAssets() throws Exception {
		AssetResponse usd = response("USD", "US Dollar", AssetType.FIAT);
		when(assetService.listActive(isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(usd)));

		mockMvc.perform(get("/api/v1/assets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("USD"))
				.andExpect(jsonPath("$.body.content[0].name").value("US Dollar"))
				.andExpect(jsonPath("$.body.content[0].type").value("FIAT"))
				.andExpect(jsonPath("$.body.content[0].status").value("ACTIVE"));
	}

	@Test
	void filtersFiat() throws Exception {
		when(assetService.listActive(eq(AssetType.FIAT), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(response("EUR", "Euro", AssetType.FIAT))));

		mockMvc.perform(get("/api/v1/assets").param("type", "FIAT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("EUR"))
				.andExpect(jsonPath("$.body.content[0].type").value("FIAT"));
	}

	@Test
	void filtersCrypto() throws Exception {
		when(assetService.listActive(eq(AssetType.CRYPTO), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(response("ETH", "Ethereum", AssetType.CRYPTO))));

		mockMvc.perform(get("/api/v1/assets").param("type", "CRYPTO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("ETH"))
				.andExpect(jsonPath("$.body.content[0].type").value("CRYPTO"));
	}

	@Test
	void filtersCommodity() throws Exception {
		when(assetService.listActive(eq(AssetType.COMMODITY), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(response("XAU", "Gold", AssetType.COMMODITY))));

		mockMvc.perform(get("/api/v1/assets").param("type", "COMMODITY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("XAU"))
				.andExpect(jsonPath("$.body.content[0].type").value("COMMODITY"));
	}

	@Test
	void searchesBySymbolOrName() throws Exception {
		when(assetService.listActive(isNull(), eq("usd"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(response("USD", "US Dollar", AssetType.FIAT))));

		mockMvc.perform(get("/api/v1/assets").param("search", "usd"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("USD"));
	}

	@Test
	void combinesCategoryAndSearch() throws Exception {
		when(assetService.listActive(eq(AssetType.CRYPTO), eq("bit"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(response("BTC", "Bitcoin", AssetType.CRYPTO))));

		mockMvc.perform(get("/api/v1/assets").param("type", "CRYPTO").param("search", "bit"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("BTC"))
				.andExpect(jsonPath("$.body.content[0].type").value("CRYPTO"));
	}

	@Test
	void getsOneAsset() throws Exception {
		AssetResponse bitcoin = response("BTC", "Bitcoin", AssetType.CRYPTO);
		when(assetService.getActive(bitcoin.id())).thenReturn(bitcoin);

		mockMvc.perform(get("/api/v1/assets/{id}", bitcoin.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(bitcoin.id().toString()))
				.andExpect(jsonPath("$.body.symbol").value("BTC"))
				.andExpect(jsonPath("$.body.status").value("ACTIVE"));
	}

	@Test
	void invalidCategoryIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/assets").param("type", "STOCK"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.title").value("Bad Request"))
				.andExpect(jsonPath("$.error.message").value("type must be one of FIAT, CRYPTO, COMMODITY"));

		verify(assetService, never()).listActive(any(), any(), any());
	}

	@Test
	void missingAssetIsNotFound() throws Exception {
		UUID id = UUID.randomUUID();
		when(assetService.getActive(id)).thenThrow(new AssetNotFoundException(id));

		mockMvc.perform(get("/api/v1/assets/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.title").value("Not Found"))
				.andExpect(jsonPath("$.error.message").value("Asset not found"));
	}

	@Test
	void inactiveAssetIsNotFound() throws Exception {
		UUID id = UUID.randomUUID();
		when(assetService.getActive(id)).thenThrow(new AssetNotFoundException(id));

		mockMvc.perform(get("/api/v1/assets/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Asset not found"));
	}

	@Test
	void malformedIdIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/assets/{id}", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("id must be a UUID"));
	}

	@Test
	void paginatesAndSortsByName() throws Exception {
		when(assetService.listActive(eq(AssetType.CRYPTO), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(
						List.of(response("BTC", "Bitcoin", AssetType.CRYPTO)),
						PageRequest.of(0, 20, Sort.by(Sort.Order.asc("name"))),
						1));

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/assets")
						.param("type", "CRYPTO")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].symbol").value("BTC"))
				.andExpect(jsonPath("$.body.page.number").value(0))
				.andExpect(jsonPath("$.body.page.size").value(20));

		verify(assetService).listActive(eq(AssetType.CRYPTO), isNull(), pageable.capture());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(20, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Order.asc("name")), pageable.getValue().getSort());
	}

	@Test
	void capsPageSize() throws Exception {
		when(assetService.listActive(isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/assets").param("page", "1").param("size", "500"))
				.andExpect(status().isOk());

		verify(assetService).listActive(isNull(), isNull(), pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(100, pageable.getValue().getPageSize());
	}

	@Test
	void rejectsNegativePage() throws Exception {
		mockMvc.perform(get("/api/v1/assets").param("page", "-1"))
				.andExpect(status().isBadRequest());

		verify(assetService, never()).listActive(any(), any(), any());
	}

	@Test
	void rejectsNonNumericPage() throws Exception {
		mockMvc.perform(get("/api/v1/assets").param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("page must be a non-negative integer"));
	}

	@Test
	void rejectsUnknownSortProperty() throws Exception {
		when(assetService.listActive(isNull(), isNull(), any(Pageable.class)))
				.thenThrow(new PropertyReferenceException(
						"notAField",
						TypeInformation.of(AssetResponse.class),
						java.util.List.of()));

		mockMvc.perform(get("/api/v1/assets").param("sort", "notAField"))
				.andExpect(status().isBadRequest());
	}

	private static AssetResponse response(String symbol, String name, AssetType type) {
		return new AssetResponse(UUID.randomUUID(), symbol, name, type, AssetStatus.ACTIVE);
	}

}
