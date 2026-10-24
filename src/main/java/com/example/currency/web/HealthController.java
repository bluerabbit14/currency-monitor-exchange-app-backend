package com.example.currency.web;

import com.example.currency.notification.dto.NotificationResponse;
import com.example.currency.notification.entity.Notification;
import com.example.currency.notification.enums.DeliveryStatus;
import com.example.currency.notification.enums.NotificationType;
import com.example.currency.notification.service.NotificationService;
import com.example.currency.notification.service.PushNotificationService;
import com.example.currency.notification.service.PushNotificationService.PushResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

	private static final String UP = "UP";
	private static final String DOWN = "DOWN";

	private final JdbcTemplate jdbcTemplate;
	private final StringRedisTemplate redisTemplate;

	private final NotificationService notificationService;

	private final PushNotificationService pushNotificationService;

	public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
			NotificationService notificationService, PushNotificationService pushNotificationService) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.notificationService = notificationService;
		this.pushNotificationService = pushNotificationService;
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

	@PostMapping("/test-notification/{userId}")
	@Operation(summary = "Send a test notification to a user",
			description = "Creates a notification for the given user with the supplied title and description.")
	public TestNotificationResponse sendTestNotification(
			@PathVariable UUID userId,
			@Valid @RequestBody TestNotificationRequest request) {
		Notification notification = notificationService.createNotification(
				userId, NotificationType.PRICE_ALERT, request.title(), request.description(), null);
		PushResult push = pushNotificationService.send(userId, request.title(), request.description());
		notification = notificationService.markDelivery(notification,
				push.sent() > 0 ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
		return new TestNotificationResponse(NotificationService.toResponse(notification), push);
	}

	public record HealthResponse(String status, String database, String redis) {
	}

	public record TestNotificationRequest(@NotBlank String title, @NotBlank String description) {
	}

	public record TestNotificationResponse(NotificationResponse notification, PushResult push) {
	}

}
