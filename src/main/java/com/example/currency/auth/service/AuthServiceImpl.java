package com.example.currency.auth.service;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpSendRequest;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {

	static final String OTP_SENT_MESSAGE = "If the number can receive an OTP, one will be sent.";

	@Override
	public OtpSendResponse sendOtp(OtpSendRequest request) {
		return new OtpSendResponse(OTP_SENT_MESSAGE);
	}

	@Override
	public AuthTokenResponse verifyOtp(OtpVerifyRequest request) {
		throw notImplemented();
	}

	@Override
	public AuthTokenResponse refreshToken(String refreshToken) {
		throw notImplemented();
	}

	@Override
	public void logout(String refreshToken) {
		throw notImplemented();
	}

	private static ResponseStatusException notImplemented() {
		return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented");
	}

}
