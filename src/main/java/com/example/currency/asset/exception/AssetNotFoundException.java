package com.example.currency.asset.exception;

import java.util.UUID;

public class AssetNotFoundException extends RuntimeException {

	public AssetNotFoundException(UUID id) {
		super("Asset not found");
	}

}
