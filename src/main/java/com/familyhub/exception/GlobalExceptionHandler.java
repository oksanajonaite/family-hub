package com.familyhub.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Domain "not found" exceptions that slip past controller catch blocks — return HTTP 404.
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({
            EventNotFoundException.class,
            TaskNotFoundException.class,
            FamilyNotFoundException.class,
            FamilyMemberNotFoundException.class,
            PetNotFoundException.class,
            NotificationNotFoundException.class,
            UserNotFoundException.class
    })
    public String handleNotFound(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/generic";
    }

    // Last-resort fallback for any unhandled exception — shows a generic error page.
    // Spring Security's AccessDeniedException must be re-thrown: if caught here,
    // the security filter chain is bypassed and the 403/redirect-to-login flow breaks.
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) throws Exception {
        if (ex instanceof AccessDeniedException) {
            throw ex;
        }
        // Avoid exposing internal error details (stack trace, DB structure) to the user
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
        return "error/generic";
    }
}
