package com.familyhub.exception;

public class ReceiptNotFoundException extends RuntimeException {

    public ReceiptNotFoundException(String message) {
        super(message);
    }
}
