package com.example.currency.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Wraps every successful controller response of this application in {@link ApiResponse}. */
@RestControllerAdvice(basePackages = "com.example.currency")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

	private static final String DEFAULT_MESSAGE = "Request successful.";

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		// Health stays a plain document so load balancers and uptime checks keep working.
		return returnType.getContainingClass() != HealthController.class;
	}

	@Override
	public Object beforeBodyWrite(
			Object body,
			MethodParameter returnType,
			MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType,
			ServerHttpRequest request,
			ServerHttpResponse response) {
		if (body instanceof ApiResponse<?> || body instanceof ProblemDetail || body instanceof String) {
			return body;
		}
		int status = response instanceof ServletServerHttpResponse servlet
				? servlet.getServletResponse().getStatus()
				: HttpStatus.OK.value();
		ApiMessage message = returnType.getMethodAnnotation(ApiMessage.class);
		return ApiResponse.success(
				status,
				message != null ? message.value() : DEFAULT_MESSAGE,
				body,
				request.getURI().getPath());
	}

}
