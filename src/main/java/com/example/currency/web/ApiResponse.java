package com.example.currency.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard envelope for every API response. Success responses carry {@code body}; failures carry
 * {@code error}. Null members are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		String status,
		int statusCode,
		String message,
		T body,
		ApiError error,
		Meta meta
) {

	public static final String SUCCESS = "SUCCESS";
	public static final String ERROR = "ERROR";

	public static <T> ApiResponse<T> success(int statusCode, String message, T body, String path) {
		return new ApiResponse<>(SUCCESS, statusCode, message, body, null, new Meta(Instant.now(), path));
	}

	public static ApiResponse<Void> failure(int statusCode, ApiError error, String path) {
		return new ApiResponse<>(ERROR, statusCode, error.message(), null, error, new Meta(Instant.now(), path));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ApiError(String code, String title, String message, List<FieldViolation> details) {
	}

	public record Meta(Instant timestamp, String path) {
	}

}
