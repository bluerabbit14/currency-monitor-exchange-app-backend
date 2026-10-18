package com.example.currency.asset.controller;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.service.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CurrencyController.class)
class CurrencyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AssetService assetService;

	@Test
	void listsAllActiveCurrenciesAsAPlainArrayWithoutAuthentication() throws Exception {
		UUID usd = UUID.randomUUID();
		when(assetService.listActiveCurrencies()).thenReturn(List.of(
				new AssetResponse(usd, "USD", "US Dollar", AssetType.FIAT, AssetStatus.ACTIVE),
				new AssetResponse(UUID.randomUUID(), "EUR", "Euro", AssetType.FIAT, AssetStatus.ACTIVE)));

		mockMvc.perform(get("/api/v1/currencies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.length()").value(2))
				.andExpect(jsonPath("$.body[0].id").value(usd.toString()))
				.andExpect(jsonPath("$.body[0].symbol").value("USD"))
				.andExpect(jsonPath("$.body[0].type").value("FIAT"))
				.andExpect(jsonPath("$.body[1].symbol").value("EUR"));
	}

}
