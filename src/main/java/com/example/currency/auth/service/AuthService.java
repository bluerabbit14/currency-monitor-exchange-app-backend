package com.example.currency.auth.service;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.OtpSendRequest;
import com.example.currency.auth.dto.OtpSendResponse;
import com.example.currency.auth.dto.OtpVerifyRequest;

public interface AuthService {

	OtpSendResponse sendOtp(OtpSendRequest request);

	AuthTokenResponse verifyOtp(OtpVerifyRequest request);

	AuthTokenResponse refreshToken(String refreshToken);

	void logout(String refreshToken);

}
