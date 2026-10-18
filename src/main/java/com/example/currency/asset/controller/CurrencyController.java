package com.example.currency.asset.controller;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currencies", description = "Currencies a user can choose as their base currency")
public class CurrencyController {

	private final AssetService assetService;

	public CurrencyController(AssetService assetService) {
		this.assetService = assetService;
	}

	@GetMapping
	@Operation(
			summary = "List all active currencies",
			description = "Returns every ACTIVE fiat currency sorted by name, in one unpaginated list. "
					+ "Use an id from this list with PUT /api/v1/users/me/base-currency.")
	public ResponseEntity<List<AssetResponse>> list() {
		return ResponseEntity.ok(assetService.listActiveCurrencies());
	}

}
