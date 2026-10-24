package com.example.currency.notification.service;

import com.example.currency.device.service.DeviceService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PushNotificationService {

	private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

	private final DeviceService deviceService;
	private final String projectId;
	private final String clientEmail;
	private final String privateKeyId;
	private final String privateKey;
	private boolean enabled;

	public PushNotificationService(DeviceService deviceService,
			@Value("${app.firebase.project-id:}") String projectId,
			@Value("${app.firebase.client-email:}") String clientEmail,
			@Value("${app.firebase.private-key-id:}") String privateKeyId,
			@Value("${app.firebase.private-key:}") String privateKey) {
		this.deviceService = deviceService;
		this.projectId = projectId;
		this.clientEmail = clientEmail;
		this.privateKeyId = privateKeyId;
		this.privateKey = privateKey;
	}

	@PostConstruct
	void init() {
		if (projectId.isBlank() || clientEmail.isBlank() || privateKey.isBlank()) {
			log.warn("FIREBASE_PROJECT_ID / FIREBASE_CLIENT_EMAIL / FIREBASE_PRIVATE_KEY are not set; "
					+ "push notifications are disabled");
			return;
		}
		try {
			if (FirebaseApp.getApps().isEmpty()) {
				GoogleCredentials credentials = ServiceAccountCredentials.fromPkcs8(
						null, clientEmail, privateKey.replace("\\n", "\n"), privateKeyId, null);
				FirebaseApp.initializeApp(FirebaseOptions.builder()
						.setProjectId(projectId)
						.setCredentials(credentials)
						.build());
			}
			enabled = true;
		}
		catch (IOException | RuntimeException ex) {
			log.error("Invalid Firebase credentials; push notifications are disabled", ex);
		}
	}

	public record PushResult(boolean firebaseConfigured, int devices, int sent, List<String> errors) {
	}

	/** Sends the push to all of the user's active devices and reports what happened. */
	public PushResult send(UUID userId, String title, String body) {
		if (!enabled) {
			return new PushResult(false, 0, 0, List.of("Firebase credentials are missing or invalid; see startup log"));
		}
		List<String> tokens = deviceService.activeTokens(userId);
		List<String> errors = new ArrayList<>();
		int sent = 0;
		for (String token : tokens) {
			Message message = Message.builder()
					.setToken(token)
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.build();
			try {
				FirebaseMessaging.getInstance().send(message);
				sent++;
			}
			catch (FirebaseMessagingException ex) {
				log.warn("FCM send failed for user {}: {} ({})", userId, ex.getMessagingErrorCode(), ex.getMessage());
				errors.add(ex.getMessagingErrorCode() + ": " + ex.getMessage());
				if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
					deviceService.deactivateByToken(userId, token);
				}
			}
		}
		if (tokens.isEmpty()) {
			errors.add("User has no active registered device");
		}
		return new PushResult(true, tokens.size(), sent, errors);
	}

}
