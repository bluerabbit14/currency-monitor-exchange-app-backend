package com.example.currency.alert.controller;

import com.example.currency.alert.dto.CreatePriceAlertRequest;
import com.example.currency.alert.dto.PriceAlertResponse;
import com.example.currency.alert.dto.UpdatePriceAlertRequest;
import com.example.currency.alert.service.PriceAlertService;
import com.example.currency.auth.service.TokenService;
import com.example.currency.config.OpenApiConfig;
import com.example.currency.web.PaginationParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/alerts")
@Tag(name = "Price Alerts", description = "Signed-in user's price alert rules for configured asset pairs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PriceAlertController {

	private final TokenService tokenService;
	private final PriceAlertService priceAlertService;

	public PriceAlertController(TokenService tokenService, PriceAlertService priceAlertService) {
		this.tokenService = tokenService;
		this.priceAlertService = priceAlertService;
	}

	@PostMapping
	@Operation(
			summary = "Create a price alert",
			description = """
					Creates an ACTIVE alert for an existing active configured asset pair.
					Does not create assets or pairs, and does not evaluate or trigger the alert.
					"""
	)
	@ApiResponse(
			responseCode = "201",
			description = "Alert created",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = PriceAlertResponse.class),
					examples = @ExampleObject(
							name = "Created alert",
							value = """
									{
									  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									  "base": "USD",
									  "quote": "INR",
									  "condition": "ABOVE",
									  "targetRate": 90.00,
									  "status": "ACTIVE",
									  "createdAt": "2026-10-12T04:00:00Z",
									  "updatedAt": "2026-10-12T04:00:00Z",
									  "triggeredAt": null
									}
									"""
					)
			)
	)
	@ApiResponse(
			responseCode = "400",
			description = "Validation failed",
			content = @Content(
					mediaType = "application/problem+json",
					examples = @ExampleObject(
							value = """
									{
									  "title": "Bad Request",
									  "status": 400,
									  "detail": "Validation failed",
									  "errors": [
									    { "field": "targetRate", "message": "targetRate must be positive" }
									  ]
									}
									"""
					)
			)
	)
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(
			mediaType = "application/problem+json",
			examples = @ExampleObject(value = """
					{
					  "title": "Unauthorized",
					  "status": 401,
					  "detail": "Invalid or expired access token"
					}
					""")
	))
	@ApiResponse(responseCode = "404", description = "Pair is not configured or inactive", content = @Content(
			mediaType = "application/problem+json",
			examples = @ExampleObject(value = """
					{
					  "title": "Not Found",
					  "status": 404,
					  "detail": "Unsupported pair: AAA/BBB",
					  "code": "UNSUPPORTED_PAIR"
					}
					""")
	))
	public ResponseEntity<PriceAlertResponse> create(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody CreatePriceAlertRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.status(HttpStatus.CREATED).body(priceAlertService.create(userId, request));
	}

	@GetMapping
	@Operation(
			summary = "List my price alerts",
			description = """
					Returns price alerts owned by the signed-in user only.
					Pages are zero-based and size is capped at 100.
					Default sort is createdAt descending.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Paginated alerts for the signed-in user",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(
							value = """
									{
									  "content": [
									    {
									      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									      "base": "USD",
									      "quote": "INR",
									      "condition": "ABOVE",
									      "targetRate": 90.00,
									      "status": "ACTIVE",
									      "createdAt": "2026-10-12T04:00:00Z",
									      "updatedAt": "2026-10-12T04:00:00Z",
									      "triggeredAt": null
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
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "400", description = "Invalid page or size")
	public ResponseEntity<Page<PriceAlertResponse>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@ParameterObject
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			HttpServletRequest request) {
		PaginationParams.validate(request.getParameter("page"), request.getParameter("size"));
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(priceAlertService.list(userId, pageable));
	}

	@GetMapping("/{alertId}")
	@Operation(
			summary = "Get one price alert",
			description = "Returns the alert only when it belongs to the signed-in user."
	)
	@ApiResponse(
			responseCode = "200",
			description = "Owned alert",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = PriceAlertResponse.class))
	)
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "404", description = "Alert not found for this user", content = @Content(
			mediaType = "application/problem+json",
			examples = @ExampleObject(value = """
					{
					  "title": "Not Found",
					  "status": 404,
					  "detail": "Alert not found"
					}
					""")
	))
	@ApiResponse(responseCode = "400", description = "The alertId is not a UUID")
	public ResponseEntity<PriceAlertResponse> get(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Alert id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID alertId) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(priceAlertService.get(userId, alertId));
	}

	@PatchMapping("/{alertId}")
	@Operation(
			summary = "Update a price alert",
			description = """
					Partially updates condition and/or targetRate for an owned alert.
					Omitted fields remain unchanged. Status is never changed by this endpoint.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Updated alert",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = PriceAlertResponse.class),
					examples = @ExampleObject(
							name = "Updated alert",
							value = """
									{
									  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
									  "base": "USD",
									  "quote": "INR",
									  "condition": "BELOW",
									  "targetRate": 89.50,
									  "status": "ACTIVE",
									  "createdAt": "2026-10-12T04:00:00Z",
									  "updatedAt": "2026-10-12T05:00:00Z",
									  "triggeredAt": null
									}
									"""
					)
			)
	)
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "404", description = "Alert not found for this user")
	public ResponseEntity<PriceAlertResponse> update(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Alert id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID alertId,
			@Valid @RequestBody UpdatePriceAlertRequest request) {
		UUID userId = tokenService.requireUserId(authorization);
		return ResponseEntity.ok(priceAlertService.update(userId, alertId, request));
	}

	@DeleteMapping("/{alertId}")
	@Operation(
			summary = "Delete a price alert",
			description = "Deletes the alert only when it belongs to the signed-in user."
	)
	@ApiResponse(responseCode = "204", description = "Alert deleted")
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	@ApiResponse(responseCode = "404", description = "Alert not found for this user")
	@ApiResponse(responseCode = "400", description = "The alertId is not a UUID")
	public ResponseEntity<Void> delete(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(description = "Alert id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
			@PathVariable UUID alertId) {
		UUID userId = tokenService.requireUserId(authorization);
		priceAlertService.delete(userId, alertId);
		return ResponseEntity.noContent().build();
	}

}
