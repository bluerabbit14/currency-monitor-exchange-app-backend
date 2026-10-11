package com.example.currency.web;

import com.example.currency.auth.exception.EmailDeliveryException;
import com.example.currency.auth.exception.InvalidContactException;
import com.example.currency.auth.exception.InvalidRefreshTokenException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.exception.PhoneOtpNotSupportedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request body is malformed");
		problem.setTitle("Bad Request");
		return handleExceptionInternal(ex, problem, headers, status, request);
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
		logger.error("Database error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Database is unavailable");
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
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setTitle(title);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
	}

	private static ProblemDetail validationProblem(HttpStatusCode status, List<FieldViolation> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
		problem.setTitle("Bad Request");
		problem.setProperty("errors", errors);
		return problem;
	}

}
