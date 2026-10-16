package com.example.currency.auth.dto;

public record OtpSendResponse(String contact, long expiresInSeconds) {
}
