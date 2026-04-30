package com.familyhub.exception;

/**
 * Thrown when a user exceeds the receipt upload rate limit (5 per hour).
 * Caught by GlobalExceptionHandler and shown as a friendly error page.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
