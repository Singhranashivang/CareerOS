package com.careeros.backend.schedule.dto;

import com.careeros.backend.schedule.PostPlatform;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;

import java.time.OffsetDateTime;

public record ScheduledPostResponse(
        Long id,
        Long achievementId,
        PostPlatform platform,
        String body,
        PostStatus status,
        OffsetDateTime scheduledFor,
        String userTimezone,
        OffsetDateTime postedAt,
        String externalPostId,
        int attemptCount,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * Must be called inside the service transaction — the achievement relation
     * is lazy.
     * ponytail: one extra select per post with an achievement. Add a projection
     * if a user's board ever grows past a few hundred rows.
     */
    public static ScheduledPostResponse from(ScheduledPost p) {
        return new ScheduledPostResponse(
                p.getId(),
                p.getAchievement() == null ? null : p.getAchievement().getId(),
                p.getPlatform(),
                p.getBody(),
                p.getStatus(),
                p.getScheduledFor(),
                p.getUserTimezone(),
                p.getPostedAt(),
                p.getExternalPostId(),
                p.getAttemptCount(),
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
