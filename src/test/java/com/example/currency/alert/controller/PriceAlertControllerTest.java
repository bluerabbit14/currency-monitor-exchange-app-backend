package com.example.currency.alert.controller;

import com.example.currency.alert.dto.CreatePriceAlertRequest;
import com.example.currency.alert.dto.PriceAlertResponse;
import com.example.currency.alert.dto.UpdatePriceAlertRequest;
import com.example.currency.alert.enums.AlertCondition;
import com.example.currency.alert.enums.AlertStatus;
import com.example.currency.alert.exception.AlertNotFoundException;
import com.example.currency.alert.service.PriceAlertService;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.config.WebDataConfig;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PriceAlertController.class, properties = "spring.data.web.pageable.max-page-size=100")
@Import({GlobalExceptionHandler.class, WebDataConfig.class})
class PriceAlertControllerTest {

	private static final String AUTH = "Bearer test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PriceAlertService priceAlertService;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void createsAlertForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.create(eq(userId), any(CreatePriceAlertRequest.class)))
				.thenReturn(response(alertId, "USD", "INR", AlertCondition.ABOVE, "90.00", AlertStatus.ACTIVE));

		mockMvc.perform(post("/api/v1/users/me/alerts")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "base": "USD",
								  "quote": "INR",
								  "condition": "ABOVE",
								  "targetRate": 90.00
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body.id").value(alertId.toString()))
				.andExpect(jsonPath("$.body.base").value("USD"))
				.andExpect(jsonPath("$.body.quote").value("INR"))
				.andExpect(jsonPath("$.body.condition").value("ABOVE"))
				.andExpect(jsonPath("$.body.targetRate").value(90.00))
				.andExpect(jsonPath("$.body.status").value("ACTIVE"));

		verify(priceAlertService).create(eq(userId), any(CreatePriceAlertRequest.class));
	}

	@Test
	void rejectsUnknownPairWith404() throws Exception {
		UUID userId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.create(eq(userId), any(CreatePriceAlertRequest.class)))
				.thenThrow(new UnsupportedPairException("AAA", "BBB"));

		mockMvc.perform(post("/api/v1/users/me/alerts")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "base": "AAA",
								  "quote": "BBB",
								  "condition": "ABOVE",
								  "targetRate": 1
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PAIR"));
	}

	@Test
	void rejectsZeroAndNegativeTargetRates() throws Exception {
		when(tokenService.requireUserId(AUTH)).thenReturn(UUID.randomUUID());

		mockMvc.perform(post("/api/v1/users/me/alerts")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "base": "USD",
								  "quote": "INR",
								  "condition": "ABOVE",
								  "targetRate": 0
								}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/users/me/alerts")
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "base": "USD",
								  "quote": "INR",
								  "condition": "ABOVE",
								  "targetRate": -1
								}
								"""))
				.andExpect(status().isBadRequest());

		verify(priceAlertService, never()).create(any(), any());
	}

	@Test
	void listsAlertsWithDefaultPaginationAndSort() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.list(eq(userId), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(
						response(alertId, "USD", "INR", AlertCondition.ABOVE, "90", AlertStatus.ACTIVE)),
						PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/users/me/alerts")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.content[0].id").value(alertId.toString()))
				.andExpect(jsonPath("$.body.content[0].base").value("USD"));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(priceAlertService).list(eq(userId), pageable.capture());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(20, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getValue().getSort());
	}

	@Test
	void retrievesOwnedAlert() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.get(userId, alertId))
				.thenReturn(response(alertId, "BTC", "USD", AlertCondition.BELOW, "60000", AlertStatus.ACTIVE));

		mockMvc.perform(get("/api/v1/users/me/alerts/{alertId}", alertId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(alertId.toString()))
				.andExpect(jsonPath("$.body.base").value("BTC"));
	}

	@Test
	void preventsAccessToAnotherUsersAlert() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.get(userId, alertId)).thenThrow(new AlertNotFoundException(alertId));

		mockMvc.perform(get("/api/v1/users/me/alerts/{alertId}", alertId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Alert not found"));
	}

	@Test
	void updatesOnlySuppliedFields() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(priceAlertService.update(eq(userId), eq(alertId), any(UpdatePriceAlertRequest.class)))
				.thenReturn(response(alertId, "USD", "INR", AlertCondition.BELOW, "89.50", AlertStatus.TRIGGERED));

		mockMvc.perform(patch("/api/v1/users/me/alerts/{alertId}", alertId)
						.header(HttpHeaders.AUTHORIZATION, AUTH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "condition": "BELOW",
								  "targetRate": 89.50
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.condition").value("BELOW"))
				.andExpect(jsonPath("$.body.targetRate").value(89.50))
				.andExpect(jsonPath("$.body.status").value("TRIGGERED"));
	}

	@Test
	void deletesOwnedAlertWith204() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);

		mockMvc.perform(delete("/api/v1/users/me/alerts/{alertId}", alertId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNoContent());

		verify(priceAlertService).delete(userId, alertId);
	}

	@Test
	void preventsDeletingAnotherUsersAlert() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		doThrow(new AlertNotFoundException(alertId)).when(priceAlertService).delete(userId, alertId);

		mockMvc.perform(delete("/api/v1/users/me/alerts/{alertId}", alertId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsUnauthenticatedRequests() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get("/api/v1/users/me/alerts"))
				.andExpect(status().isUnauthorized());

		verify(priceAlertService, never()).list(any(), any());
	}

	private static PriceAlertResponse response(
			UUID id,
			String base,
			String quote,
			AlertCondition condition,
			String targetRate,
			AlertStatus status) {
		Instant createdAt = Instant.parse("2026-10-12T04:00:00Z");
		return new PriceAlertResponse(
				id,
				base,
				quote,
				condition,
				new BigDecimal(targetRate),
				status,
				createdAt,
				createdAt,
				null);
	}

}
