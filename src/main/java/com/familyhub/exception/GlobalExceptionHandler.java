package com.familyhub.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Catches any unhandled exception and shows a generic error page.
     * Business logic exceptions (UserAlreadyExists, etc.) are handled
     * directly in controllers via try-catch or BindingResult.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/generic";
    }
}
