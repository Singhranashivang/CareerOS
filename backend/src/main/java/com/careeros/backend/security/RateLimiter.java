package com.careeros.backend.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One bucket per (user, tier), held in memory. This is a single-instance
 * app — nothing here is deployed behind a load balancer, so an in-memory
 * map is the actual right size for this, not a first cut waiting to be
 * swapped for Redis. Revisit only if the app becomes multi-instance.
 */
@Component
public class RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public ConsumptionProbe tryConsume(Long userId, RateLimitTier tier) {
        Bucket bucket = buckets.computeIfAbsent(
                userId + ":" + tier,
                key -> Bucket.builder().addLimit(tier.bandwidth()).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    /**
     * For background callers (the weekly digest job) rather than an HTTP
     * request: nobody is waiting on a response, so waiting out the same
     * bucket a live request would get 429'd against is the right behaviour,
     * not a separate unlimited path. Shares the exact bucket tryConsume
     * uses, so a user's background run and their own live traffic draw from
     * the same budget.
     */
    public void consumeBlocking(Long userId, RateLimitTier tier) throws InterruptedException {
        while (true) {
            ConsumptionProbe probe = tryConsume(userId, tier);
            if (probe.isConsumed()) {
                return;
            }
            Thread.sleep(Duration.ofNanos(probe.getNanosToWaitForRefill()).toMillis() + 50);
        }
    }
}
