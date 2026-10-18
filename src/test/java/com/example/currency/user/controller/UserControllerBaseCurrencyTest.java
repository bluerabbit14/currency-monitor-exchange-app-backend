package com.example.currency.user.controller;

import com.example.currency.asset.exception.CurrencyNotFoundException;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
class UserControllerBaseCurrencyTest {

	private static final String URL = "/api/v1/users/me/base-currency";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TokenService tokenService;

	@MockitoBean
	private UserProfileService userProfileService;

	@Test
	void setsTheBaseCurrencyForTheSignedInUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID currencyId = UUID.randomUUID();
		when(tokenService.requireUserId("Bearer t")).thenReturn(userId);
		when(userProfileService.setBaseCurrency(userId, currencyId)).thenReturn(
				new UserProfileResponse(userId, "a@b.co", null, null, null, null, null, true));

		mockMvc.perform(put(URL).header("Authorization", "Bearer t")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currencyId\":\"" + currencyId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(userId.toString()));
		verify(userProfileService).setBaseCurrency(userId, currencyId);
	}

	@Test
	void anUnknownOrNonCurrencyIdIs404() throws Exception {
		UUID currencyId = UUID.randomUUID();
		when(tokenService.requireUserId(any())).thenReturn(UUID.randomUUID());
		when(userProfileService.setBaseCurrency(any(), any())).thenThrow(new CurrencyNotFoundException());

		mockMvc.perform(put(URL).header("Authorization", "Bearer t")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currencyId\":\"" + currencyId + "\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Currency not found"));
	}

	@Test
	void aMissingOrMalformedCurrencyIdIs400() throws Exception {
		when(tokenService.requireUserId(any())).thenReturn(UUID.randomUUID());

		mockMvc.perform(put(URL).header("Authorization", "Bearer t")
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("currencyId"));
		mockMvc.perform(put(URL).header("Authorization", "Bearer t")
						.contentType(MediaType.APPLICATION_JSON).content("{\"currencyId\":\"nope\"}"))
				.andExpect(status().isBadRequest());
		verify(userProfileService, never()).setBaseCurrency(any(), any());
	}

	@Test
	void requiresAValidAccessToken() throws Exception {
		when(tokenService.requireUserId(any())).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
						.content("{\"currencyId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isUnauthorized());
		verify(userProfileService, never()).setBaseCurrency(any(), any());
	}

}
