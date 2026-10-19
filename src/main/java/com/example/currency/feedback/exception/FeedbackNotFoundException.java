package com.example.currency.feedback.exception;

public class FeedbackNotFoundException extends RuntimeException {

	public FeedbackNotFoundException() {
		super("Feedback not found");
	}

}
