package com.example.currency.plan.controller;

import com.example.currency.plan.dto.PlanResponse;
import com.example.currency.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans", description = "Read-only catalog of subscription plans and their features")
public class PlanController {

	private final PlanService planService;

	public PlanController(PlanService planService) {
		this.planService = planService;
	}

	@GetMapping
	@Operation(
			summary = "List available plans with their features",
			description = """
					Returns every active plan. Each feature has a key, an enabled flag, and a limit.
					A null limit on an enabled feature means unlimited.
					"""
	)
	@ApiResponse(
			responseCode = "200",
			description = "Active plans",
			content = @Content(
					mediaType = "application/json",
					examples = @ExampleObject(value = """
							[
							  {
							    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
							    "code": "FREE",
							    "name": "Free",
							    "isDefault": true,
							    "features": [
							      { "key": "max_active_alerts", "enabled": true, "limit": 5 },
							      { "key": "percent_alerts", "enabled": false, "limit": null }
							    ]
							  }
							]
							""")
			)
	)
	public ResponseEntity<List<PlanResponse>> list() {
		return ResponseEntity.ok(planService.listActive());
	}

}
