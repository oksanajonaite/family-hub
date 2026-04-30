package com.familyhub.exception;

/**
 * Thrown when Gemini API call fails or the response cannot be parsed.
 * ReceiptService catches this to mark the Receipt as FAILED.
 */
public class GeminiParsingException extends RuntimeException {

    public GeminiParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
