package com.familyhub.exception;

public class FamilyNotFoundException extends RuntimeException {
    public FamilyNotFoundException(Long id) {
        super("Family not found: " + id);
    }
}
