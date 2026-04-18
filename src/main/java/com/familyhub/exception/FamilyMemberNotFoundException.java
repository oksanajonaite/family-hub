package com.familyhub.exception;

public class FamilyMemberNotFoundException extends RuntimeException {
    public FamilyMemberNotFoundException(Long id) {
        super("Family member not found: " + id);
    }
}
