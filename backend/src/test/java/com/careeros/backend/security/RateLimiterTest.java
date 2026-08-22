package com.careeros.backend.security;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void readsAllowsSixtyThenRejectsTheSixtyFirst() {
        for (int i = 0; i < 60; i++) {
            assertThat(rateLimiter.tryConsume(1L, RateLimitTier.READS).isConsumed()).isTrue();
        }

        ConsumptionProbe probe = rateLimiter.tryConsume(1L, RateLimitTier.READS);
        assertThat(probe.isConsumed()).isFalse();
        assertThat(probe.getNanosToWaitForRefill()).isPositive();
    }

    @Test
    void syncAllowsFiveThenRejectsTheSixth() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume(2L, RateLimitTier.SYNC).isConsumed()).isTrue();
        }

        assertThat(rateLimiter.tryConsume(2L, RateLimitTier.SYNC).isConsumed()).isFalse();
    }

    @Test
    void analyzeAllowsThreeThenRejectsTheFourth() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(3L, RateLimitTier.ANALYZE).isConsumed()).isTrue();
        }

        assertThat(rateLimiter.tryConsume(3L, RateLimitTier.ANALYZE).isConsumed()).isFalse();
    }

    @Test
    void linkedinPostAllowsTenThenRejectsTheEleventh() {
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryConsume(4L, RateLimitTier.LINKEDIN_POST).isConsumed()).isTrue();
        }

        assertThat(rateLimiter.tryConsume(4L, RateLimitTier.LINKEDIN_POST).isConsumed()).isFalse();
    }

    @Test
    void differentUsersGetIndependentBuckets() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.tryConsume(10L, RateLimitTier.ANALYZE);
        }
        assertThat(rateLimiter.tryConsume(10L, RateLimitTier.ANALYZE).isConsumed()).isFalse();

        // A different user's ANALYZE budget is untouched by user 10 exhausting theirs.
        assertThat(rateLimiter.tryConsume(11L, RateLimitTier.ANALYZE).isConsumed()).isTrue();
    }

    @Test
    void differentTiersForTheSameUserAreIndependent() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.tryConsume(20L, RateLimitTier.ANALYZE);
        }
        assertThat(rateLimiter.tryConsume(20L, RateLimitTier.ANALYZE).isConsumed()).isFalse();

        // Exhausting ANALYZE doesn't touch the same user's READS budget.
        assertThat(rateLimiter.tryConsume(20L, RateLimitTier.READS).isConsumed()).isTrue();
    }
}
