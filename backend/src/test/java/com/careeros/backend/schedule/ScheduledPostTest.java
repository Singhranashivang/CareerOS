package com.careeros.backend.schedule;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Retry/backoff transitions — the one bit of publisher logic with real branches. */
class ScheduledPostTest {

    private static ScheduledPost publishing() {
        return ScheduledPost.builder()
                .platform(PostPlatform.LINKEDIN)
                .body("body")
                .status(PostStatus.PUBLISHING)
                .claimedBy("worker-1")
                .claimedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void failureBelowLimitReschedulesWithSquaredBackoff() {
        ScheduledPost post = publishing();
        OffsetDateTime before = OffsetDateTime.now();

        post.recordFailure("boom", PostPublisher.MAX_ATTEMPTS);

        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(post.getAttemptCount()).isEqualTo(1);
        assertThat(post.getFailureReason()).isEqualTo("boom");
        // attempt 1 -> 1 minute
        assertThat(post.getScheduledFor()).isBetween(
                before.plusMinutes(1).minusSeconds(5), before.plusMinutes(1).plusSeconds(5));
        assertThat(post.getClaimedBy()).isNull();
    }

    @Test
    void backoffGrowsWithAttempts() {
        ScheduledPost post = publishing();
        OffsetDateTime before = OffsetDateTime.now();

        post.recordFailure("a", PostPublisher.MAX_ATTEMPTS);
        post.setStatus(PostStatus.PUBLISHING);
        post.recordFailure("b", PostPublisher.MAX_ATTEMPTS);

        assertThat(post.getAttemptCount()).isEqualTo(2);
        // attempt 2 -> 4 minutes
        assertThat(post.getScheduledFor()).isAfter(before.plusMinutes(3));
    }

    @Test
    void failureAtLimitIsTerminal() {
        ScheduledPost post = publishing();

        for (int i = 0; i < PostPublisher.MAX_ATTEMPTS; i++) {
            post.setStatus(PostStatus.PUBLISHING);
            post.recordFailure("boom " + i, PostPublisher.MAX_ATTEMPTS);
        }

        assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
        assertThat(post.getAttemptCount()).isEqualTo(PostPublisher.MAX_ATTEMPTS);
        assertThat(post.getClaimedAt()).isNull();
    }

    @Test
    void successClearsClaimAndFailure() {
        ScheduledPost post = publishing();
        post.recordFailure("earlier failure", PostPublisher.MAX_ATTEMPTS);
        post.setStatus(PostStatus.PUBLISHING);

        post.recordSuccess("urn:li:share:123");

        assertThat(post.getStatus()).isEqualTo(PostStatus.POSTED);
        assertThat(post.getExternalPostId()).isEqualTo("urn:li:share:123");
        assertThat(post.getPostedAt()).isNotNull();
        assertThat(post.getFailureReason()).isNull();
        assertThat(post.getClaimedBy()).isNull();
    }
}
