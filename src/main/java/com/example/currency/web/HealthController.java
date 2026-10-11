package com.example.currency.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping
	@Operation(summary = "Check that the application and database are reachable")
	public HealthResponse health() {
		Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		if (result == null || result != 1) {
			throw new IllegalStateException("Database connectivity check failed");
		}
		return new HealthResponse("UP", "UP");
	}

	public record HealthResponse(String status, String database) {
	}

}
