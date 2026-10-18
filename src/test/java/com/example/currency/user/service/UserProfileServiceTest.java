package com.example.currency.user.service;

import com.example.currency.asset.dto.AssetResponse;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.CurrencyNotFoundException;
import com.example.currency.notification.entity.NotificationPreference;
import com.example.currency.notification.repository.NotificationPreferenceRepository;
import com.example.currency.plan.dto.PlanFeatureResponse;
import com.example.currency.plan.dto.PlanResponse;
import com.example.currency.user.dto.UserPlanResponse;
import java.util.List;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.repository.RefreshSessionRepository;
import com.example.currency.asset.service.AssetService;
import com.example.currency.deletion.config.DeletionProperties;
import com.example.currency.plan.service.PlanService;
import com.example.currency.systemlog.service.SystemLogService;
import com.example.currency.user.dto.UpdateUserProfileRequest;
import com.example.currency.user.dto.UserProfileResponse;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshSessionRepository refreshSessionRepository;

	@Mock
	private SystemLogService systemLog;

	@Mock
	private PlanService planService;

	@Mock
	private AssetService assetService;

	@Mock
	private NotificationPreferenceRepository notificationPreferenceRepository;

	private UserProfileService userProfileService;

	@BeforeEach
	void setUp() {
		userProfileService = new UserProfileService(
				userRepository, refreshSessionRepository, new DeletionProperties(), systemLog, planService, assetService,
				notificationPreferenceRepository);
		lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void getProfileReturnsTheActiveUser() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.getProfile(user.getId());

		assertEquals(new UserProfileResponse(
				user.getId(),
				"ada@example.com",
				"+15551212",
				"Ada",
				"https://cdn.example.com/ada.png",
				null,
				null,
				true), response);
		verify(userRepository, never()).save(any());
	}

	@Test
	void getProfileExposesOnlyThePlanIdAndNameAndTheNotificationSetting() {
		User user = activeUser();
		UUID planId = UUID.randomUUID();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(planService.currentPlanFor(user.getId())).thenReturn(new PlanResponse(
				planId, "FREE", "Free", true,
				List.of(new PlanFeatureResponse("max_favorites", true, 10))));
		NotificationPreference preference = new NotificationPreference();
		preference.setPushEnabled(false);
		when(notificationPreferenceRepository.findByUserId(user.getId())).thenReturn(Optional.of(preference));

		UserProfileResponse response = userProfileService.getProfile(user.getId());

		assertEquals(new UserPlanResponse(planId, "Free"), response.currentPlan());
		assertEquals(false, response.notificationsEnabled());
		verify(notificationPreferenceRepository, never()).save(any());
		verify(notificationPreferenceRepository, never()).saveAndFlush(any());
	}

	@Test
	void getProfileDoesNotSerialiseTimestamps() throws Exception {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		String json = new com.fasterxml.jackson.databind.ObjectMapper()
				.writeValueAsString(userProfileService.getProfile(user.getId()));

		assertEquals(false, json.contains("createdAt"));
		assertEquals(false, json.contains("updatedAt"));
		assertEquals(true, json.contains("\"notificationsEnabled\":true"));
	}

	@Test
	void setBaseCurrencyStoresAnActiveFiatCurrencyAndReturnsTheProfile() {
		User user = activeUser();
		UUID currencyId = UUID.randomUUID();
		AssetResponse usd = new AssetResponse(currencyId, "USD", "US Dollar", AssetType.FIAT, AssetStatus.ACTIVE);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(assetService.findActiveCurrency(currencyId)).thenReturn(Optional.of(usd));
		when(assetService.findActive(currencyId)).thenReturn(Optional.of(usd));

		UserProfileResponse response = userProfileService.setBaseCurrency(user.getId(), currencyId);

		assertEquals(currencyId, user.getBaseAssetId());
		assertEquals(usd, response.baseCurrency());
		verify(userRepository).save(user);
	}

	@Test
	void setBaseCurrencyRejectsAnythingThatIsNotAnActiveCurrencyAndChangesNothing() {
		User user = activeUser();
		UUID previous = UUID.randomUUID();
		user.setBaseAssetId(previous);
		UUID bitcoin = UUID.randomUUID();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(assetService.findActiveCurrency(bitcoin)).thenReturn(Optional.empty());

		assertThrows(CurrencyNotFoundException.class, () -> userProfileService.setBaseCurrency(user.getId(), bitcoin));

		assertEquals(previous, user.getBaseAssetId());
		verify(userRepository, never()).save(any());
	}

	@Test
	void setBaseCurrencyRejectsAnInactiveUser() {
		User user = activeUser();
		user.setStatus(UserStatus.SUSPENDED);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertThrows(InvalidAccessTokenException.class,
				() -> userProfileService.setBaseCurrency(user.getId(), UUID.randomUUID()));
	}

	@Test
	void patchUpdatesOnlyProvidedFields() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(
				user.getId(),
				new UpdateUserProfileRequest("  Grace Hopper  ", null));

		assertEquals("Grace Hopper", response.displayName());
		assertEquals("https://cdn.example.com/ada.png", response.profileImageUrl());
		assertEquals("ada@example.com", response.email());
		assertEquals("+15551212", response.phoneNumber());
	}

	@Test
	void patchClearsBlankFields() {
		User user = activeUser();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse response = userProfileService.updateProfile(
				user.getId(),
				new UpdateUserProfileRequest("   ", " \t "));

		assertNull(response.displayName());
		assertNull(response.profileImageUrl());
	}

	@Test
	void rejectsInactiveUser() {
		User user = activeUser();
		user.setStatus(UserStatus.SUSPENDED);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertThrows(InvalidAccessTokenException.class, () -> userProfileService.getProfile(user.getId()));
		verify(userRepository, never()).save(any());
	}

	private static User activeUser() {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
		Instant updatedAt = Instant.parse("2026-02-01T00:00:00Z");
		setField(user, "createdAt", createdAt);
		setField(user, "updatedAt", updatedAt);
		user.setEmail("ada@example.com");
		user.setPhoneNumber("+15551212");
		user.setDisplayName("Ada");
		user.setProfileImageUrl("https://cdn.example.com/ada.png");
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> type = target.getClass();
		while (type != null) {
			try {
				var field = type.getDeclaredField(name);
				field.setAccessible(true);
				field.set(target, value);
				return;
			}
			catch (NoSuchFieldException ex) {
				type = type.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new IllegalStateException(name);
	}

}
