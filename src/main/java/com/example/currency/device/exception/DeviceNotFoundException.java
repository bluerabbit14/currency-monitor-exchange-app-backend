package com.example.currency.device.exception;

import java.util.UUID;

public class DeviceNotFoundException extends RuntimeException {

	public DeviceNotFoundException(UUID id) {
		super("Device not found");
	}

}
