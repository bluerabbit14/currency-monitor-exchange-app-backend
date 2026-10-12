package com.example.currency.device.service;

import com.example.currency.device.dto.DeviceResponse;
import com.example.currency.device.dto.RegisterDeviceRequest;
import com.example.currency.device.dto.UpdateDeviceRequest;
import com.example.currency.device.exception.DeviceNotFoundException;
import com.example.currency.device.repository.UserDeviceRepository;
import com.example.currency.user.entity.UserDevice;
import com.example.currency.user.enums.DevicePlatform;
import com.example.currency.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {

	private final UserDeviceRepository userDeviceRepository;
	private final UserRepository userRepository;

	public DeviceService(UserDeviceRepository userDeviceRepository, UserRepository userRepository) {
		this.userDeviceRepository = userDeviceRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public DeviceResponse register(UUID userId, RegisterDeviceRequest request) {
		if (request.platform() != DevicePlatform.ANDROID) {
			throw new IllegalArgumentException("platform must be ANDROID");
		}

		String token = request.fcmToken().trim();
		return userDeviceRepository.findByDeviceToken(token)
				.map(device -> refreshExisting(device, userId, request.deviceName()))
				.orElseGet(() -> createDevice(userId, token, request));
	}

	@Transactional(readOnly = true)
	public List<DeviceResponse> list(UUID userId) {
		return userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
				.map(DeviceService::toResponse)
				.toList();
	}

	@Transactional
	public DeviceResponse update(UUID userId, UUID deviceId, UpdateDeviceRequest request) {
		UserDevice device = requireOwned(userId, deviceId);
		if (request.deviceName() != null) {
			device.setDeviceName(blankToNull(request.deviceName()));
			device = userDeviceRepository.save(device);
		}
		return toResponse(device);
	}

	@Transactional
	public void unregister(UUID userId, UUID deviceId) {
		UserDevice device = requireOwned(userId, deviceId);
		if (device.isActive()) {
			device.setActive(false);
			userDeviceRepository.save(device);
		}
	}

	private DeviceResponse createDevice(UUID userId, String token, RegisterDeviceRequest request) {
		UserDevice device = new UserDevice();
		device.setUser(userRepository.getReferenceById(userId));
		device.setDeviceToken(token);
		device.setPlatform(DevicePlatform.ANDROID);
		device.setDeviceName(blankToNull(request.deviceName()));
		device.setActive(true);
		device.setLastSeenAt(Instant.now());
		try {
			return toResponse(userDeviceRepository.saveAndFlush(device));
		}
		catch (DataIntegrityViolationException ex) {
			return userDeviceRepository.findByDeviceToken(token)
					.map(existing -> refreshExisting(existing, userId, request.deviceName()))
					.orElseThrow(() -> ex);
		}
	}

	private DeviceResponse refreshExisting(UserDevice device, UUID userId, String deviceName) {
		device.setUser(userRepository.getReferenceById(userId));
		device.setPlatform(DevicePlatform.ANDROID);
		device.setActive(true);
		device.setLastSeenAt(Instant.now());
		if (deviceName != null) {
			device.setDeviceName(blankToNull(deviceName));
		}
		return toResponse(userDeviceRepository.save(device));
	}

	private UserDevice requireOwned(UUID userId, UUID deviceId) {
		return userDeviceRepository.findByIdAndUserId(deviceId, userId)
				.orElseThrow(() -> new DeviceNotFoundException(deviceId));
	}

	private static DeviceResponse toResponse(UserDevice device) {
		return new DeviceResponse(
				device.getId(),
				device.getPlatform(),
				device.getDeviceName(),
				device.isActive(),
				device.getCreatedAt(),
				device.getUpdatedAt(),
				device.getLastSeenAt());
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
