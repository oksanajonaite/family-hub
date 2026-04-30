package com.familyhub.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for receipt uploads — powered by Bucket4j.
 *
 * Each user gets their own token bucket with configurable capacity and refill period.
 * "Greedy" refill means tokens are added continuously — a user who exhausts their
 * limit does not have to wait for the full refill window to restart.
 *
 * Buckets are created lazily on first upload and kept for the lifetime of the
 * application. Memory footprint is negligible (one Bucket ≈ a few hundred bytes).
 *
 * Limits are configurable via application properties:
 *   receipt.rate-limit.capacity=5
 *   receipt.rate-limit.refill-hours=1
 */
@Slf4j
@Service
public class ReceiptRateLimiterService {

    @Value("${receipt.rate-limit.capacity:5}")
    private int capacity;

    @Value("${receipt.rate-limit.refill-hours:1}")
    private int refillHours;

    private Bandwidth limit;

    // userId → Bucket — ConcurrentHashMap is thread-safe for concurrent uploads
    private final ConcurrentHashMap<Long, Bucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        this.limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofHours(refillHours))
                .build();
        log.info("Receipt rate limiter configured: {} uploads per {} hour(s)", capacity, refillHours);
    }

    /**
     * Attempts to consume one token for the given user.
     *
     * @return {@code true} if the upload is allowed, {@code false} if the limit is reached
     */
    public boolean tryConsume(Long userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, id ->
                Bucket.builder()
                        .addLimit(limit)
                        .build()
        );
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for user {} ({} uploads per {} hour(s))",
                    userId, capacity, refillHours);
        }
        return allowed;
    }
}
