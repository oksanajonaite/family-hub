package com.familyhub.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Cache holidays by calendar year so we avoid repeated external API calls
        // while still naturally loading a fresh entry when a new year is opened.
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("publicHolidaysByYear");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(8)
                .expireAfterWrite(Duration.ofDays(400)));
        return cacheManager;
    }
}
