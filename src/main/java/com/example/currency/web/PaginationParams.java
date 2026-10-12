package com.example.currency.web;

public final class PaginationParams {

	private PaginationParams() {
	}

	public static void validate(String page, String size) {
		if (page != null && !page.isBlank()) {
			try {
				if (Integer.parseInt(page.trim()) < 0) {
					throw new IllegalArgumentException("page must be a non-negative integer");
				}
			}
			catch (NumberFormatException ex) {
				throw new IllegalArgumentException("page must be a non-negative integer");
			}
		}
		if (size != null && !size.isBlank()) {
			try {
				if (Integer.parseInt(size.trim()) < 1) {
					throw new IllegalArgumentException("size must be a positive integer");
				}
			}
			catch (NumberFormatException ex) {
				throw new IllegalArgumentException("size must be a positive integer");
			}
		}
	}

}
