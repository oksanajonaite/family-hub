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
     *
     * Spring Security's AccessDeniedException must be re-thrown so that
     * Spring Security can handle it itself (redirect to /login or return 403).
     * If we catch it here, the security filter chain is bypassed.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) throws Exception {
        if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            throw ex;
        }
        // Avoid exposing internal error details (stack trace, DB structure) to the user
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
        return "error/generic";
    }
}
