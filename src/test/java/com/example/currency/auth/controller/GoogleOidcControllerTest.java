package com.example.currency.auth.controller;

import com.example.currency.auth.dto.AuthTokenResponse;
import com.example.currency.auth.dto.GoogleOidcResponse;
import com.example.currency.auth.exception.AccountNotActiveException;
import com.example.currency.auth.exception.GoogleAccountConflictException;
import com.example.currency.auth.exception.GoogleEmailNotVerifiedException;
import com.example.currency.auth.exception.GoogleSignInUnavailableException;
import com.example.currency.auth.exception.InvalidGoogleTokenException;
import com.example.currency.auth.exception.OtpVerificationFailedException;
import com.example.currency.auth.service.GoogleOidcService;
import com.example.currency.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GoogleOidcController.class)
@Import(GlobalExceptionHandler.class)
class GoogleOidcControllerTest {

	private static final String SIGN_IN = "/api/v1/auth/oidc/google";
	private static final String VERIFY = "/api/v1/auth/oidc/google/link/verify";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GoogleOidcService service;

	@Test
	void signedInReturnsYakkuTokensAndNoLinkFields() throws Exception {
		when(service.login("id-token")).thenReturn(GoogleOidcResponse.signedIn(
				new AuthTokenResponse("access", "refresh", "Bearer", 900), true));

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"id-token\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.status").value("SIGNED_IN"))
				.andExpect(jsonPath("$.body.accessToken").value("access"))
				.andExpect(jsonPath("$.body.refreshToken").value("refresh"))
				.andExpect(jsonPath("$.body.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.body.expiresInSeconds").value(900))
				.andExpect(jsonPath("$.body.newUser").value(true))
				.andExpect(jsonPath("$.body.linkToken").doesNotExist());
	}

	@Test
	void linkRequiredReturnsTheLinkTokenButNoYakkuTokens() throws Exception {
		when(service.login("id-token")).thenReturn(GoogleOidcResponse.linkRequired("link-secret", "pe***@example.com", 300));

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"id-token\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.status").value("LINK_VERIFICATION_REQUIRED"))
				.andExpect(jsonPath("$.body.linkToken").value("link-secret"))
				.andExpect(jsonPath("$.body.maskedEmail").value("pe***@example.com"))
				.andExpect(jsonPath("$.body.linkExpiresInSeconds").value(300))
				.andExpect(jsonPath("$.body.accessToken").doesNotExist())
				.andExpect(jsonPath("$.body.refreshToken").doesNotExist());
	}

	@Test
	void aMissingOrBlankIdTokenIsABadRequestAndNeverReachesTheService() throws Exception {
		for (String body : new String[] {"{}", "{\"idToken\":\"\"}", "{\"idToken\":\"   \"}", "{\"idToken\":null}"}) {
			mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON)
						.content("{\"idToken\":\"" + "x".repeat(8193) + "\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void aClientCannotSupplyAnEmailOrUserIdToInfluenceWhoIsSignedIn() throws Exception {
		when(service.login("id-token")).thenReturn(GoogleOidcResponse.signedIn(
				new AuthTokenResponse("a", "r", "Bearer", 900), false));

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON)
						.content("{\"idToken\":\"id-token\",\"email\":\"victim@example.com\",\"userId\":\"1\"}"))
				.andExpect(status().isOk());

		verify(service).login("id-token");
	}

	@Test
	void invalidTokenIs401() throws Exception {
		when(service.login(anyString())).thenThrow(new InvalidGoogleTokenException());

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"forged\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.message").value("Invalid Google ID token"));
	}

	@Test
	void unverifiedEmailIs403WithACode() throws Exception {
		when(service.login(anyString())).thenThrow(new GoogleEmailNotVerifiedException());

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void conflictIs409AndAnInactiveAccountIs403() throws Exception {
		when(service.login("conflict")).thenThrow(new GoogleAccountConflictException("already linked"));
		when(service.login("suspended")).thenThrow(new AccountNotActiveException());

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"conflict\"}"))
				.andExpect(status().isConflict());
		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"suspended\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void googleSignInNotConfiguredOrUnreachableIs503() throws Exception {
		when(service.login(anyString())).thenThrow(new GoogleSignInUnavailableException("Google sign-in is not configured"));

		mockMvc.perform(post(SIGN_IN).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t\"}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void verifyLinkReturnsTokensOnSuccess() throws Exception {
		when(service.verifyLink("link-secret", "123456")).thenReturn(GoogleOidcResponse.signedIn(
				new AuthTokenResponse("access", "refresh", "Bearer", 900), false));

		mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON)
						.content("{\"linkToken\":\"link-secret\",\"otp\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.status").value("SIGNED_IN"))
				.andExpect(jsonPath("$.body.accessToken").value("access"))
				.andExpect(jsonPath("$.body.newUser").value(false));
	}

	@Test
	void verifyLinkRejectsAnInvalidExpiredOrExhaustedOtpWith401() throws Exception {
		when(service.verifyLink(anyString(), anyString())).thenThrow(new OtpVerificationFailedException());

		mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON)
						.content("{\"linkToken\":\"link-secret\",\"otp\":\"123456\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.message").value("Invalid or expired OTP."));
	}

	@Test
	void verifyLinkValidatesItsInput() throws Exception {
		for (String body : new String[] {
				"{}",
				"{\"linkToken\":\"t\"}",
				"{\"otp\":\"123456\"}",
				"{\"linkToken\":\"\",\"otp\":\"123456\"}",
				"{\"linkToken\":\"t\",\"otp\":\"12345\"}",
				"{\"linkToken\":\"t\",\"otp\":\"abcdef\"}",
				"{\"linkToken\":\"t\",\"otp\":\"1234567\"}"}) {
			mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verify(service, never()).verifyLink(anyString(), anyString());
	}

}
