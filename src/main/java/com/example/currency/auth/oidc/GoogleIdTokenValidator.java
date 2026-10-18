package com.example.currency.auth.oidc;

import com.example.currency.auth.exception.GoogleSignInUnavailableException;
import com.example.currency.auth.exception.InvalidGoogleTokenException;

public interface GoogleIdTokenValidator {

	/**
	 * @throws InvalidGoogleTokenException         when the token is malformed, mis-signed, expired, or issued by or
	 *                                             for anyone other than Google and the configured client
	 * @throws GoogleSignInUnavailableException    when Google sign-in is not configured or Google's keys cannot be fetched
	 */
	VerifiedGoogleIdentity verify(String idToken);

}
