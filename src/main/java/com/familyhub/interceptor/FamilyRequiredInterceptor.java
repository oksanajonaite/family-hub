package com.familyhub.interceptor;

import com.familyhub.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Redirects unauthenticated or family-less users to /family/setup
 * before they can access any family-scoped page (/receipts/**, /spending/**).
 *
 * This removes the repeated  if (familyId == null) return "redirect:..."
 * that was scattered across every controller method.
 */
@Component
public class FamilyRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            if (user.getFamilyId() == null) {
                response.sendRedirect(request.getContextPath() + "/family/setup");
                return false;
            }
        }

        return true;
    }
}
