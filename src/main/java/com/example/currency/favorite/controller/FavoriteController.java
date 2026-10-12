package com.example.currency.favorite.controller;

import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.favorite.dto.FavoriteAssetResponse;
import com.example.currency.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/favorites")
@Tag(name = "Favorites", description = "Signed-in user's favorite assets")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class FavoriteController {

	private final TokenService tokenService;
	private final FavoriteService favoriteService;

	public FavoriteController(TokenService tokenService, FavoriteService favoriteService) {
		this.tokenService = tokenService;
		this.favoriteService = favoriteService;
	}

	@PostMapping("/{assetId}")
	@Operation(
			summary = "Add an asset to favorites",
			description = """
					Favorites the given active asset for the signed-in user.
					Repeating the same request is idempotent and does not create a duplicate row.
					Inactive or unknown assets are rejected as not found.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Favorite created or already present",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = FavoriteAssetResponse.class),
					examples = @ExampleObject(
							name = "Favorited asset",
							value = """
									{
									  "assetId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									  "symbol": "BTC",
									  "name": "Bitcoin",
									  "type": "CRYPTO",
									  "status": "ACTIVE",
									  "createdAt": "2026-10-12T04:00:00Z"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "404",
			description = "The asset does not exist or is inactive",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Not Found",
									  "status": 404,
									  "detail": "Asset not found"
									}
									"""
					)
			)
	)
	@ApiResponse(responseCode = "400", description = "The assetId is not a UUID")
	public ResponseEntity<FavoriteAssetResponse> add(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Asset id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID assetId) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(favoriteService.add(userId, assetId));
	}

	@GetMapping
	@Operation(
			summary = "List favorite assets",
			description = """
					Returns the signed-in user's favorite assets that are still ACTIVE.
					Results are ordered newest-first by when each asset was favorited.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Favorite assets for the signed-in user",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							value = """
									[
									  {
									    "assetId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									    "symbol": "BTC",
									    "name": "Bitcoin",
									    "type": "CRYPTO",
									    "status": "ACTIVE",
									    "createdAt": "2026-10-12T04:00:00Z"
									  }
									]
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	public ResponseEntity<List<FavoriteAssetResponse>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(favoriteService.list(userId));
	}

	@DeleteMapping("/{assetId}")
	@Operation(
			summary = "Remove an asset from favorites",
			description = """
					Removes the favorite relationship for the signed-in user only.
					The asset itself is not modified. Removing a favorite that does not exist
					still returns 204 No Content.
					"""
	)
	@ApiResponse(responseCode = "204", description = "Favorite removed, or it was not present")
	@ApiResponse(
			responseCode = "401",
			description = "Missing or invalid access token",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Unauthorized",
									  "status": 401,
									  "detail": "Invalid or expired access token"
									}
									"""
					)
			)
	)
	@ApiResponse(responseCode = "400", description = "The assetId is not a UUID")
	public ResponseEntity<Void> remove(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Asset id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID assetId) {
		UUID userId = tokenService.requireUserId(authorization);
		favoriteService.remove(userId, assetId);
		return ResponseEntity.noContent().build();
	}

}
