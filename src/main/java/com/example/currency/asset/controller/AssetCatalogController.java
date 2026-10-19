package com.example.currency.asset.controller;

import com.example.currency.asset.dto.CurrencyItemResponse;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.service.CurrencyCatalogService;
import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets/grouped")
@Tag(name = "Assets", description = "Active assets grouped by type, flagged with the user's favorites")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AssetCatalogController {

	private final CurrencyCatalogService catalogService;
	private final TokenService tokenService;

	public AssetCatalogController(CurrencyCatalogService catalogService, TokenService tokenService) {
		this.catalogService = catalogService;
		this.tokenService = tokenService;
	}

	@GetMapping
	@Operation(
			summary = "List all active assets grouped by type",
			description = "Requires a Bearer access token in the Authorization header. Returns every ACTIVE asset "
					+ "in one unpaginated object keyed by type (FIAT, CRYPTO, COMMODITY), each list sorted by name. "
					+ "`favorite` is true when the signed-in user has favorited the asset.")
	public ResponseEntity<Map<AssetType, List<CurrencyItemResponse>>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(catalogService.listGroupedByType(userId));
	}

}
