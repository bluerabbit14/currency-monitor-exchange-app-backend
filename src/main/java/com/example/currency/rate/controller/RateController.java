package com.example.currency.rate.controller;

import com.example.currency.asset.enums.AssetType;
import com.example.currency.rate.dto.RateHistoryResponse;
import com.example.currency.rate.dto.RateResponse;
import com.example.currency.rate.service.RateQueryService;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/rates")
@Tag(
		name = "Rates",
		description = """
				Read-only exchange rates from the application database.
				Rates are never fetched on demand from external providers.
				Scheduled collection is not enabled yet.
				"""
)
public class RateController {

	private final RateQueryService rateQueryService;

	public RateController(RateQueryService rateQueryService) {
		this.rateQueryService = rateQueryService;
	}

	@GetMapping
	@Operation(
			summary = "List current rates",
			description = """
					Returns cached current rates for explicitly configured active asset pairs only.
					Optional filters: type (base asset category), base symbol, quote symbol.
					Pages are zero-based and size is capped at 100.
					A rate is stale when observedAt is older than app.rates.stale-after (default PT15M).
					Does not call external providers or create pairs dynamically.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Paginated current rates",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							name = "Crypto quote USD",
							value = """
									{
									  "content": [
									    {
									      "base": "BTC",
									      "quote": "USD",
									      "rate": 65000.123456789012,
									      "observedAt": "2026-10-12T08:00:00Z",
									      "fetchedAt": "2026-10-12T08:00:05Z",
									      "stale": false,
									      "providerCode": "sample"
									    }
									  ],
									  "totalElements": 1,
									  "totalPages": 1,
									  "size": 20,
									  "number": 0
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Invalid type or pagination",
			content = @Content(mediaType = "application/problem+json")
	)
	public ResponseEntity<Page<RateResponse>> list(
			@Parameter(
					description = "Base asset category",
					example = "CRYPTO",
					schema = @Schema(allowableValues = {"FIAT", "CRYPTO", "COMMODITY"})
			)
			@RequestParam(required = false) AssetType type,
			@Parameter(description = "Base asset symbol", example = "BTC")
			@RequestParam(required = false) String base,
			@Parameter(description = "Quote asset symbol", example = "USD")
			@RequestParam(required = false) String quote,
			@ParameterObject
			@PageableDefault(size = 20, sort = "observedAt", direction = Sort.Direction.DESC) Pageable pageable,
			HttpServletRequest request) {
		PaginationParams.validate(request.getParameter("page"), request.getParameter("size"));
		return ResponseEntity.ok(rateQueryService.listCurrentRates(type, base, quote, pageable));
	}

	@GetMapping("/{base}/{quote}")
	@Operation(
			summary = "Get one current rate",
			description = """
					Returns the cached current rate for an active configured pair.
					Returns the latest cached value even when stale=true.
					Returns 404 with code RATE_NOT_AVAILABLE when the pair is configured but has no cached rate.
					Returns 404 with code UNSUPPORTED_PAIR when the pair is not configured or inactive.
					Does not fetch a new rate from an external provider.
					"""
	)
	@ApiResponse(responseCode = "200", description = "Cached current rate")
	@ApiResponse(
			responseCode = "404",
			description = "Unsupported pair or rate not available",
			content = @Content(
					mediaType = "application/problem+json",
					examples = {
							@ExampleObject(
									name = "Rate not available",
									value = """
											{
											  "title": "Not Found",
											  "status": 404,
											  "detail": "Rate not available for pair: USD/INR",
											  "code": "RATE_NOT_AVAILABLE"
											}
											"""
							),
							@ExampleObject(
									name = "Unsupported pair",
									value = """
											{
											  "title": "Not Found",
											  "status": 404,
											  "detail": "Unsupported pair: AAA/BBB",
											  "code": "UNSUPPORTED_PAIR"
											}
											"""
							)
					}
			)
	)
	public ResponseEntity<RateResponse> get(
			@Parameter(description = "Base asset symbol", example = "USD")
			@PathVariable String base,
			@Parameter(description = "Quote asset symbol", example = "INR")
			@PathVariable String quote) {
		return ResponseEntity.ok(rateQueryService.getCurrentRate(base, quote));
	}

	@GetMapping("/{base}/{quote}/history")
	@Operation(
			summary = "Get rate history",
			description = """
					Returns stored historical observations for an active configured pair.
					Interval is [from, to): inclusive from, exclusive to.
					When omitted, to defaults to now (UTC) and from defaults to 30 days before to.
					Sorted by observedAt descending, then id descending.
					Does not invent observations or call external providers.
					"""
	)
	@ApiResponse(responseCode = "200", description = "Paginated history observations")
	@ApiResponse(
			responseCode = "400",
			description = "Invalid date range or pagination",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							name = "Invalid range",
							value = """
									{
									  "title": "Bad Request",
									  "status": 400,
									  "detail": "from must be earlier than to",
									  "code": "INVALID_RATE_HISTORY_RANGE"
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "404",
			description = "Unsupported pair",
			content = @Content(mediaType = "application/problem+json")
	)
	public ResponseEntity<Page<RateHistoryResponse>> history(
			@Parameter(description = "Base asset symbol", example = "USD")
			@PathVariable String base,
			@Parameter(description = "Quote asset symbol", example = "INR")
			@PathVariable String quote,
			@Parameter(description = "Inclusive start instant (ISO-8601)", example = "2026-10-01T00:00:00Z")
			@RequestParam(required = false) Instant from,
			@Parameter(description = "Exclusive end instant (ISO-8601)", example = "2026-10-12T00:00:00Z")
			@RequestParam(required = false) Instant to,
			@ParameterObject
			@PageableDefault(size = 100, sort = {"observedAt", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable,
			HttpServletRequest request) {
		PaginationParams.validate(request.getParameter("page"), request.getParameter("size"));
		return ResponseEntity.ok(rateQueryService.getHistory(base, quote, from, to, pageable));
	}

}
