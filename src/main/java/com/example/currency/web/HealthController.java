package com.example.currency.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

	private static final String UP = "UP";
	private static final String DOWN = "DOWN";

	private final JdbcTemplate jdbcTemplate;
	private final StringRedisTemplate redisTemplate;

	public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
	}

	@GetMapping
	@Operation(summary = "Check that the application, database and Redis are reachable",
			description = "Returns 200 when everything is UP and 503 when any dependency is DOWN.")
	public ResponseEntity<HealthResponse> health() {
		String database = databaseStatus();
		String redis = redisStatus();
		boolean healthy = UP.equals(database) && UP.equals(redis);
		HealthResponse body = new HealthResponse(healthy ? UP : DOWN, database, redis);
		return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

	private String databaseStatus() {
		try {
			Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return result != null && result == 1 ? UP : DOWN;
		}
		catch (RuntimeException ex) {
			log.warn("Database health check failed", ex);
			return DOWN;
		}
	}

	private String redisStatus() {
		try {
			String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
			return "PONG".equalsIgnoreCase(pong) ? UP : DOWN;
		}
		catch (RuntimeException ex) {
			log.warn("Redis health check failed", ex);
			return DOWN;
		}
	}

	public record HealthResponse(String status, String database, String redis) {
	}

}
