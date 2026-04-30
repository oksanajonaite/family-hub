package com.familyhub.config;

import com.familyhub.interceptor.FamilyRequiredInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration — registers application-level interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FamilyRequiredInterceptor familyRequiredInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Guard family-scoped pages — redirect to /family/setup if user has no family yet
        registry.addInterceptor(familyRequiredInterceptor)
                .addPathPatterns("/receipts/**", "/spending/**");
    }
}
