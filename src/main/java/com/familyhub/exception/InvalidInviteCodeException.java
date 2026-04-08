package com.familyhub.exception;

public class InvalidInviteCodeException extends RuntimeException {
    public InvalidInviteCodeException() {
        super("Invite code is invalid or has expired.");
    }
}
