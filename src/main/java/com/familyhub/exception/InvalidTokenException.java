package com.familyhub.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("This password reset link is invalid or has expired.");
    }
}
