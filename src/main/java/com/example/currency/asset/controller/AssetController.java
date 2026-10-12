package com.example.currency.asset.controller;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.service.AssetService;
import com.example.currency.web.PaginationParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Assets", description = "Read-only catalog of assets this application supports")
public class AssetController {

	private final AssetService assetService;

	public AssetController(AssetService assetService) {
		this.assetService = assetService;
	}

	@GetMapping
	@Operation(
			summary = "List active assets",
			description = """
					Returns assets whose status is ACTIVE. Inactive assets are omitted.
					Categories are FIAT, CRYPTO, and COMMODITY. An unknown category is rejected.
					Search is case-insensitive and matches symbol or name. Category and search combine with AND.
					Pages are zero-based, size is capped at 100, and the default sort is name ascending.
					Examples: ?type=FIAT, ?type=CRYPTO, ?type=COMMODITY, ?search=usd, ?search=bitcoin, ?type=CRYPTO&search=bit, ?type=CRYPTO&page=0&size=20.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Active assets",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							name = "Crypto search",
							description = "GET /api/v1/assets?type=CRYPTO&search=bit&page=0&size=20",
							value = """
									{
									  "content": [
									    {
									      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									      "symbol": "BTC",
									      "name": "Bitcoin",
									      "type": "CRYPTO",
									      "status": "ACTIVE"
									    }
									  ],
									  "page": {
									    "size": 20,
									    "number": 0,
									    "totalElements": 1,
									    "totalPages": 1
									  }
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Invalid category or pagination",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							name = "Invalid category",
							description = "GET /api/v1/assets?type=STOCK",
							value = """
									{
									  "title": "Bad Request",
									  "status": 400,
									  "detail": "type must be one of FIAT, CRYPTO, COMMODITY"
									}
									"""
					)
			)
	)
	public ResponseEntity<Page<AssetResponse>> list(
			@Parameter(
					description = "Asset category",
					example = "FIAT",
					schema = @Schema(allowableValues = {"FIAT", "CRYPTO", "COMMODITY"})
			)
			@RequestParam(required = false) AssetType type,
			@Parameter(description = "Case-insensitive match against symbol or name", example = "bitcoin")
			@RequestParam(required = false) String search,
			@ParameterObject
			@PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
			HttpServletRequest request) {
		PaginationParams.validate(request.getParameter("page"), request.getParameter("size"));
		return ResponseEntity.ok(assetService.listActive(type, search, pageable));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get an active asset")
	@ApiResponse(responseCode = "200", description = "The active asset")
	@ApiResponse(
			responseCode = "404",
			description = "The asset does not exist or is inactive",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							name = "Missing or inactive",
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
	@ApiResponse(responseCode = "400", description = "The id is not a UUID")
	public ResponseEntity<AssetResponse> get(
			@Parameter(description = "Asset id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID id) {
		return ResponseEntity.ok(assetService.getActive(id));
	}

}
