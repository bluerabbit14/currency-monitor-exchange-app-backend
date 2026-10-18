package com.example.currency.asset.exception;

/** The id is not an active fiat currency (missing, inactive, or another asset type). */
public class CurrencyNotFoundException extends RuntimeException {

	public CurrencyNotFoundException() {
		super("Currency not found");
	}

}
