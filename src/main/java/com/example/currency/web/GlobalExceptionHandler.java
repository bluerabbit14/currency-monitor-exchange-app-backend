package com.example.currency.web;

import com.example.currency.alert.exception.AlertNotFoundException;
import com.example.currency.notification.exception.EmptyNotificationPreferenceUpdateException;
import com.example.currency.notification.exception.NotificationNotFoundException;
import com.example.currency.asset.enums.AssetType;
import com.example.currency.asset.exception.AssetNotFoundException;
import com.example.currency.auth.exception.EmailDeliveryException;
import com.example.currency.device.exception.DeviceNotFoundException;
import com.example.currency.auth.exception.InvalidAccessTokenException;
import com.example.currency.auth.exception.InvalidContactException;
import com.example.currency.auth.exception.InvalidRefreshTokenException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import com.example.currency.rate.exception.InvalidRateHistoryRangeException;
import com.example.currency.rate.exception.InvalidRateObservationException;
import com.example.currency.rate.exception.RateNotAvailableException;
import com.example.currency.rate.exception.UnsupportedPairException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** Every error path ends here; ProblemDetail bodies are re-shaped into the ApiResponse envelope. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex,
			Object body,
			HttpHeaders headers,
			HttpStatusCode statusCode,
			WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response == null || !(response.getBody() instanceof ProblemDetail problem)) {
			return response;
		}
		String path = request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
		Object code = problem.getProperties() == null ? null : problem.getProperties().get("code");
		Object errors = problem.getProperties() == null ? null : problem.getProperties().get("errors");
		@SuppressWarnings("unchecked")
		List<FieldViolation> details = errors instanceof List<?> list ? (List<FieldViolation>) list : null;
		String title = problem.getTitle();
		ApiResponse.ApiError error = new ApiResponse.ApiError(
				code != null ? code.toString() : defaultCode(statusCode, details),
				title,
				problem.getDetail(),
				details);
		return ResponseEntity.status(statusCode)
				.headers(response.getHeaders())
				.body(ApiResponse.failure(statusCode.value(), error, path));
	}

	private static String defaultCode(HttpStatusCode status, List<FieldViolation> details) {
		if (details != null) {
			return "VALIDATION_FAILED";
		}
		HttpStatus resolved = HttpStatus.resolve(status.value());
		return resolved == null ? "ERROR" : resolved.name();
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = validationProblem(status, errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		List<FieldViolation> errors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String parameterName = result.getMethodParameter().getParameterName();
			result.getResolvableErrors().forEach(error -> {
				String field = error instanceof FieldError fieldError
						? fieldError.getField()
						: parameterName;
				errors.add(new FieldViolation(field == null ? "request" : field, error.getDefaultMessage()));
			});
		});
		ProblemDetail problem = validationProblem(status, errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		String name = ex instanceof MethodArgumentTypeMismatchException mismatch
				? mismatch.getName()
				: ex.getPropertyName();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, typeMismatchDetail(name, ex.getRequiredType()));
		problem.setTitle("Bad Request");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request body is malformed");
		problem.setTitle("Bad Request");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(AssetNotFoundException.class)
	ResponseEntity<Object> handleAssetNotFound(AssetNotFoundException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", request);
	}

	@ExceptionHandler(DeviceNotFoundException.class)
	ResponseEntity<Object> handleDeviceNotFound(DeviceNotFoundException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", request);
	}

	@ExceptionHandler(AlertNotFoundException.class)
	ResponseEntity<Object> handleAlertNotFound(AlertNotFoundException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", request);
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	ResponseEntity<Object> handleNotificationNotFound(NotificationNotFoundException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", request);
	}

	@ExceptionHandler(EmptyNotificationPreferenceUpdateException.class)
	ResponseEntity<Object> handleEmptyNotificationPreferenceUpdate(
			EmptyNotificationPreferenceUpdateException ex,
			WebRequest request) {
		ProblemDetail problem = validationProblem(
				HttpStatus.BAD_REQUEST,
				List.of(new FieldViolation("request", ex.getMessage())));
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(UnsupportedPairException.class)
	ResponseEntity<Object> handleUnsupportedPair(UnsupportedPairException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", UnsupportedPairException.CODE, request);
	}

	@ExceptionHandler(RateNotAvailableException.class)
	ResponseEntity<Object> handleRateNotAvailable(RateNotAvailableException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_FOUND, "Not Found", RateNotAvailableException.CODE, request);
	}

	@ExceptionHandler(InvalidRateHistoryRangeException.class)
	ResponseEntity<Object> handleInvalidRateHistoryRange(InvalidRateHistoryRangeException ex, WebRequest request) {
		return problem(ex, HttpStatus.BAD_REQUEST, "Bad Request", InvalidRateHistoryRangeException.CODE, request);
	}

	@ExceptionHandler(InvalidRateObservationException.class)
	ResponseEntity<Object> handleInvalidRateObservation(InvalidRateObservationException ex, WebRequest request) {
		return problem(ex, HttpStatus.BAD_REQUEST, "Bad Request", request);
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ResponseEntity<Object> handleUnknownSortProperty(PropertyReferenceException ex, WebRequest request) {
		return problem(ex, HttpStatus.BAD_REQUEST, "Bad Request", request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
		return problem(ex, HttpStatus.BAD_REQUEST, "Bad Request", request);
	}

	@ExceptionHandler(InvalidContactException.class)
	ResponseEntity<Object> handleInvalidContact(InvalidContactException ex, WebRequest request) {
		return problem(ex, HttpStatus.BAD_REQUEST, "Bad Request", request);
	}

	@ExceptionHandler(OtpVerificationFailedException.class)
	ResponseEntity<Object> handleOtpVerificationFailed(OtpVerificationFailedException ex, WebRequest request) {
		return problem(ex, HttpStatus.UNAUTHORIZED, "Unauthorized", request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	ResponseEntity<Object> handleInvalidRefreshToken(InvalidRefreshTokenException ex, WebRequest request) {
		return problem(ex, HttpStatus.UNAUTHORIZED, "Unauthorized", request);
	}

	@ExceptionHandler(InvalidAccessTokenException.class)
	ResponseEntity<Object> handleInvalidAccessToken(InvalidAccessTokenException ex, WebRequest request) {
		return problem(ex, HttpStatus.UNAUTHORIZED, "Unauthorized", request);
	}

	@ExceptionHandler(PhoneOtpNotSupportedException.class)
	ResponseEntity<Object> handlePhoneOtpNotSupported(PhoneOtpNotSupportedException ex, WebRequest request) {
		return problem(ex, HttpStatus.NOT_IMPLEMENTED, "Not Implemented", request);
	}

	@ExceptionHandler(EmailDeliveryException.class)
	ResponseEntity<Object> handleEmailDelivery(EmailDeliveryException ex, WebRequest request) {
		return problem(ex, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
		List<FieldViolation> errors = ex.getConstraintViolations().stream()
				.map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
				.toList();
		ProblemDetail problem = validationProblem(HttpStatus.BAD_REQUEST, errors);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<Object> handleDataAccess(DataAccessException ex, WebRequest request) {
		logger.error("Data store error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.SERVICE_UNAVAILABLE,
				"A required data store is unavailable");
		problem.setTitle("Service Unavailable");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		logger.error("Unhandled request error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Unexpected error");
		problem.setTitle("Internal Server Error");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	private ResponseEntity<Object> problem(RuntimeException ex, HttpStatus status, String title, WebRequest request) {
		return problem(ex, status, title, null, request);
	}

	private ResponseEntity<Object> problem(
			RuntimeException ex,
			HttpStatus status,
			String title,
			String code,
			WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setTitle(title);
		if (code != null) {
			problem.setProperty("code", code);
		}
		return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
	}

	private static ProblemDetail validationProblem(HttpStatusCode status, List<FieldViolation> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
		problem.setTitle("Bad Request");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static String typeMismatchDetail(String name, Class<?> requiredType) {
		if (requiredType == AssetType.class || "type".equals(name)) {
			return "type must be one of FIAT, CRYPTO, COMMODITY";
		}
		if (requiredType == Instant.class || "from".equals(name) || "to".equals(name)) {
			return "from and to must be ISO-8601 timestamps";
		}
		if ("page".equals(name)) {
			return "page must be a non-negative integer";
		}
		if ("size".equals(name)) {
			return "size must be a positive integer";
		}
		if (requiredType == UUID.class || "id".equals(name)) {
			return "id must be a UUID";
		}
		return "Request parameter is invalid";
	}

}
