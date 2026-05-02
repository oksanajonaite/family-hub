package com.familyhub.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // MaxUploadSizeExceededException is thrown by Spring's multipart resolver BEFORE the controller
    // method is called — so RedirectAttributes is not available here.
    // We write directly to the FlashMap (Spring MVC's internal flash mechanism) to pass the error
    // message through the redirect without losing it.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public RedirectView handleMaxUploadSize(HttpServletRequest request) {
        //temporary place to save message
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        //redirect path/address
        String requestUri = request.getRequestURI();
        String redirectPath = resolveUploadRedirectPath(requestUri);
        //name by which the error message will be accessible on the html page
        String flashKey = resolveUploadFlashKey(requestUri);
        String message = resolveUploadErrorMessage(requestUri);

        if (flashMap != null) {
            flashMap.put(flashKey, message);
            flashMap.setTargetRequestPath(redirectPath);
        }
        return new RedirectView(redirectPath);
    }

    // Custom permission denial — HTTP 403. Separate from Spring Security's AccessDeniedException,
    // which is re-thrown below so the security filter chain can redirect to login correctly.
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public String handleForbidden(ForbiddenException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/generic";
    }

    // Domain "not found" exceptions that slip past controller catch blocks — return HTTP 404.
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({
            EventNotFoundException.class,
            TaskNotFoundException.class,
            FamilyNotFoundException.class,
            FamilyMemberNotFoundException.class,
            PetNotFoundException.class,
            NotificationNotFoundException.class,
            UserNotFoundException.class,
            ReceiptNotFoundException.class
    })
    public String handleNotFound(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/generic";
    }

    // Receipt upload rate limit exceeded — HTTP 429 Too Many Requests.
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitExceededException.class)
    public String handleRateLimit(RateLimitExceededException ex, Model model) {
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
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        // Avoid exposing internal error details (stack trace, DB structure) to the user
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
        return "error/generic";
    }

    private String resolveUploadRedirectPath(String requestUri) {
        if (requestUri == null) {
            return "/family";
        }
        if (requestUri.startsWith("/receipts/upload")) {
            return "/receipts/upload";
        }
        return "/family";
    }

    private String resolveUploadFlashKey(String requestUri) {
        if (requestUri != null && requestUri.startsWith("/profile/avatar")) {
            return "profileError";
        }
        return "errorMessage";
    }

    private String resolveUploadErrorMessage(String requestUri) {
        if (requestUri != null && requestUri.startsWith("/receipts/upload")) {
            return "Receipt photo is too large. Maximum allowed size is 5 MB per photo.";
        }
        return "Photo is too large. Maximum allowed size is 5 MB.";
    }
}
