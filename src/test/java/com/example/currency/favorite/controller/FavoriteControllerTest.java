package com.example.currency.favorite.controller;

import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.service.TokenService;
import com.example.currency.favorite.dto.FavoriteAssetResponse;
import com.example.currency.favorite.service.FavoriteService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FavoriteController.class)
@Import(GlobalExceptionHandler.class)
class FavoriteControllerTest {

	private static final String AUTH = "Bearer test-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FavoriteService favoriteService;

	@MockitoBean
	private TokenService tokenService;

	@Test
	void addsFavoriteForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(favoriteService.add(userId, assetId)).thenReturn(response(assetId, "BTC", Instant.parse("2026-10-12T04:00:00Z")));

		mockMvc.perform(post("/api/v1/users/me/favorites/{assetId}", assetId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.assetId").value(assetId.toString()))
				.andExpect(jsonPath("$.body.symbol").value("BTC"))
				.andExpect(jsonPath("$.body.type").value("CRYPTO"))
				.andExpect(jsonPath("$.body.createdAt").value("2026-10-12T04:00:00Z"));

		verify(favoriteService).add(userId, assetId);
	}

	@Test
	void listsFavoritesForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(favoriteService.list(userId)).thenReturn(List.of(
				response(assetId, "ETH", Instant.parse("2026-10-11T00:00:00Z"))));

		mockMvc.perform(get("/api/v1/users/me/favorites")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body[0].assetId").value(assetId.toString()))
				.andExpect(jsonPath("$.body[0].symbol").value("ETH"));

		verify(favoriteService).list(userId);
	}

	@Test
	void removesFavoriteForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);

		mockMvc.perform(delete("/api/v1/users/me/favorites/{assetId}", assetId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNoContent());

		verify(favoriteService).remove(userId, assetId);
	}

	@Test
	void rejectsUnauthenticatedRequests() throws Exception {
		when(tokenService.requireUserId(null)).thenThrow(new InvalidAccessTokenException());

		mockMvc.perform(get("/api/v1/users/me/favorites"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.title").value("Unauthorized"))
				.andExpect(jsonPath("$.error.message").value("Invalid or expired access token"));

		verify(favoriteService, never()).list(any());
	}

	@Test
	void rejectsMissingAsset() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		when(tokenService.requireUserId(AUTH)).thenReturn(userId);
		when(favoriteService.add(userId, assetId)).thenThrow(new AssetNotFoundException(assetId));

		mockMvc.perform(post("/api/v1/users/me/favorites/{assetId}", assetId)
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("Asset not found"));
	}

	@Test
	void rejectsInvalidAssetId() throws Exception {
		mockMvc.perform(post("/api/v1/users/me/favorites/{assetId}", "not-a-uuid")
						.header(HttpHeaders.AUTHORIZATION, AUTH))
				.andExpect(status().isBadRequest());

		verify(favoriteService, never()).add(any(), any());
	}

	private static FavoriteAssetResponse response(UUID assetId, String symbol, Instant createdAt) {
		return new FavoriteAssetResponse(
				assetId,
				symbol,
				symbol + " Name",
				AssetType.CRYPTO,
				AssetStatus.ACTIVE,
				createdAt);
	}

}
