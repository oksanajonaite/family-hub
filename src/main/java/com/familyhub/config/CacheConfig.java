package com.familyhub.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine cache configuration.
 *
 * SimpleCacheManager lets each cache have its own Caffeine spec (size + TTL).
 * CaffeineCacheManager applies a single spec to all — not suitable here.
 *
 * Caches:
 *   publicHolidaysByYear  — external API, refresh yearly (400d TTL)
 *   spendingByCategory    — DB aggregation per family+month, refresh on new receipt (6h TTL)
 *   spendingMonthlyTotals — DB aggregation per family, refresh on new receipt (6h TTL)
 *   spendingInsight       — Gemini text per family, refresh on new receipt (24h TTL)
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(

            // Public holidays — external Nager.Date API, cached per calendar year
            new CaffeineCache("publicHolidaysByYear", Caffeine.newBuilder()
                    .maximumSize(8)
                    .expireAfterWrite(Duration.ofDays(400))
                    .build()),

            // Spending breakdown per family + month — evicted on new receipt upload
            new CaffeineCache("spendingByCategory", Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(Duration.ofHours(6))
                    .build()),

            // Monthly totals (bar chart) per family — evicted on new receipt upload
            new CaffeineCache("spendingMonthlyTotals", Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(Duration.ofHours(6))
                    .build()),

            // Gemini spending insight — evicted on new receipt upload; 24h TTL as safety net.
            // Gemini cost is negligible (~$0.00003/call) but unnecessary calls add latency.
            new CaffeineCache("spendingInsight", Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(Duration.ofHours(24))
                    .build())
        ));
        return cacheManager;
    }
}
