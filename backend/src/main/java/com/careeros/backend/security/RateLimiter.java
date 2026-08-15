package com.careeros.backend.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

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
}
