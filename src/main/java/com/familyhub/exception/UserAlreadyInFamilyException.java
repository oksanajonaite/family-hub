package com.familyhub.exception;

public class UserAlreadyInFamilyException extends RuntimeException {
    public UserAlreadyInFamilyException() {
        super("User already belongs to a family.");
    }
}
