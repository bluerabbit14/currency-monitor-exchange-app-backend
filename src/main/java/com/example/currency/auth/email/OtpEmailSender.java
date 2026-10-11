package com.example.currency.auth.email;

public interface OtpEmailSender {

	void sendOtp(String email, String otp);

}
