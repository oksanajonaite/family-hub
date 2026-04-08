package com.familyhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    // Spring Boot auto-configuration handles the defaults.
    // Add custom view resolvers, interceptors, or formatters here as needed.
}
