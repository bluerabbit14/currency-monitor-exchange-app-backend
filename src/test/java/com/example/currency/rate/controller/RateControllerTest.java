package com.example.currency.rate.controller;

import com.example.currency.asset.enums.AssetType;
import com.example.currency.config.WebDataConfig;
import com.example.currency.rate.dto.RateHistoryResponse;
import com.example.currency.rate.dto.RateResponse;
import com.example.currency.rate.exception.InvalidRateHistoryRangeException;
import com.example.currency.rate.exception.RateNotAvailableException;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.rate.service.RateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

@WebMvcTest(controllers = RateController.class, properties = "spring.data.web.pageable.max-page-size=100")
@Import(WebDataConfig.class)
class RateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RateQueryService rateQueryService;

	@Test
	void listsCurrentRates() throws Exception {
		RateResponse rate = rate("BTC", "USD", "65000", false);
		when(rateQueryService.listCurrentRates(isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(rate)));

		mockMvc.perform(get("/api/v1/rates"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].base").value("BTC"))
				.andExpect(jsonPath("$.content[0].quote").value("USD"))
				.andExpect(jsonPath("$.content[0].stale").value(false));
	}

	@Test
	void filtersListByTypeBaseAndQuote() throws Exception {
		when(rateQueryService.listCurrentRates(eq(AssetType.CRYPTO), eq("BTC"), eq("USD"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(rate("BTC", "USD", "65000", true))));

		mockMvc.perform(get("/api/v1/rates")
						.param("type", "CRYPTO")
						.param("base", "BTC")
						.param("quote", "USD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].base").value("BTC"))
				.andExpect(jsonPath("$.content[0].stale").value(true));
	}

	@Test
	void getsOneCurrentRate() throws Exception {
		when(rateQueryService.getCurrentRate("USD", "INR"))
				.thenReturn(rate("USD", "INR", "83.1", false));

		mockMvc.perform(get("/api/v1/rates/{base}/{quote}", "USD", "INR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.base").value("USD"))
				.andExpect(jsonPath("$.quote").value("INR"))
				.andExpect(jsonPath("$.rate").value(83.1))
				.andExpect(jsonPath("$.providerCode").value("test-provider"));
	}

	@Test
	void getsHistory() throws Exception {
		when(rateQueryService.getHistory(eq("USD"), eq("INR"), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(history("83.1"))));

		mockMvc.perform(get("/api/v1/rates/{base}/{quote}/history", "USD", "INR")
						.param("from", "2026-10-01T00:00:00Z")
						.param("to", "2026-10-12T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].rate").value(83.1))
				.andExpect(jsonPath("$.content[0].providerCode").value("test-provider"));
	}

	@Test
	void invalidTypeIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/rates").param("type", "STOCK"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("type must be one of FIAT, CRYPTO, COMMODITY"));

		verify(rateQueryService, never()).listCurrentRates(any(), any(), any(), any());
	}

	@Test
	void invalidInstantIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/rates/{base}/{quote}/history", "USD", "INR")
						.param("from", "not-an-instant"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("from and to must be ISO-8601 timestamps"));
	}

	@Test
	void invalidHistoryRangeIsBadRequest() throws Exception {
		when(rateQueryService.getHistory(eq("USD"), eq("INR"), any(), any(), any(Pageable.class)))
				.thenThrow(new InvalidRateHistoryRangeException("from must be earlier than to"));

		mockMvc.perform(get("/api/v1/rates/{base}/{quote}/history", "USD", "INR")
						.param("from", "2026-10-12T00:00:00Z")
						.param("to", "2026-10-01T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RATE_HISTORY_RANGE"))
				.andExpect(jsonPath("$.detail").value("from must be earlier than to"));
	}

	@Test
	void capsPageSize() throws Exception {
		when(rateQueryService.listCurrentRates(isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/rates").param("page", "1").param("size", "500"))
				.andExpect(status().isOk());

		verify(rateQueryService).listCurrentRates(isNull(), isNull(), isNull(), pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(100, pageable.getValue().getPageSize());
	}

	@Test
	void rejectsNegativePage() throws Exception {
		mockMvc.perform(get("/api/v1/rates").param("page", "-1"))
				.andExpect(status().isBadRequest());

		verify(rateQueryService, never()).listCurrentRates(any(), any(), any(), any());
	}

	@Test
	void missingCachedRateIsNotFound() throws Exception {
		when(rateQueryService.getCurrentRate("USD", "INR"))
				.thenThrow(new RateNotAvailableException("USD", "INR"));

		mockMvc.perform(get("/api/v1/rates/{base}/{quote}", "USD", "INR"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RATE_NOT_AVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Rate not available for pair: USD/INR"));
	}

	@Test
	void unsupportedPairIsNotFound() throws Exception {
		when(rateQueryService.getCurrentRate("AAA", "BBB"))
				.thenThrow(new UnsupportedPairException("AAA", "BBB"));

		mockMvc.perform(get("/api/v1/rates/{base}/{quote}", "AAA", "BBB"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_PAIR"))
				.andExpect(jsonPath("$.detail").value("Unsupported pair: AAA/BBB"));
	}

	@Test
	void appliesDefaultListSort() throws Exception {
		when(rateQueryService.listCurrentRates(isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(
						List.of(),
						PageRequest.of(0, 20, Sort.by(Sort.Order.desc("observedAt"))),
						0));

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/rates"))
				.andExpect(status().isOk());

		verify(rateQueryService).listCurrentRates(isNull(), isNull(), isNull(), pageable.capture());
		assertEquals(Sort.by(Sort.Order.desc("observedAt")), pageable.getValue().getSort());
	}

	private static RateResponse rate(String base, String quote, String value, boolean stale) {
		return new RateResponse(
				base,
				quote,
				new BigDecimal(value),
				Instant.parse("2026-10-12T08:00:00Z"),
				Instant.parse("2026-10-12T08:00:05Z"),
				stale,
				"test-provider");
	}

	private static RateHistoryResponse history(String value) {
		return new RateHistoryResponse(
				new BigDecimal(value),
				Instant.parse("2026-10-12T08:00:00Z"),
				Instant.parse("2026-10-12T08:00:05Z"),
				"test-provider");
	}

}
