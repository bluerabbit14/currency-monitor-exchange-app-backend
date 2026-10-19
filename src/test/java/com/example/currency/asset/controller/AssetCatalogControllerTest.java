package com.example.currency.asset.controller;

import com.example.currency.asset.dto.CurrencyItemResponse;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.service.CurrencyCatalogService;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssetCatalogController.class)
class AssetCatalogControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CurrencyCatalogService catalogService;

	@MockitoBean
	private TokenService tokenService;

	private static Map<AssetType, List<CurrencyItemResponse>> catalog(UUID usd, boolean favorite) {
		Map<AssetType, List<CurrencyItemResponse>> grouped = new EnumMap<>(AssetType.class);
		grouped.put(AssetType.FIAT, List.of(
				new CurrencyItemResponse(usd, "USD", "US Dollar", favorite, AssetStatus.ACTIVE)));
		grouped.put(AssetType.CRYPTO, List.of(
				new CurrencyItemResponse(UUID.randomUUID(), "BTC", "Bitcoin", false, AssetStatus.ACTIVE)));
		grouped.put(AssetType.COMMODITY, List.of());
		return grouped;
	}

	@Test
	void rejectsRequestsWithoutAValidBearerToken() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get("/api/v1/assets/grouped"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(catalogService);
	}

	@Test
	void signedInCallersGetTheirFavoritesFlagged() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID usd = UUID.randomUUID();
		when(tokenService.requireUserId("Bearer t")).thenReturn(userId);
		when(catalogService.listGroupedByType(userId)).thenReturn(catalog(usd, true));

		mockMvc.perform(get("/api/v1/assets/grouped").header("Authorization", "Bearer t"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.FIAT[0].favorite").value(true))
				.andExpect(jsonPath("$.body.CRYPTO[0].favorite").value(false));
	}

}
