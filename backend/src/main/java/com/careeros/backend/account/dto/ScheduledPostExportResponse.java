package com.careeros.backend.account.dto;

import com.careeros.backend.schedule.PostPlatform;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;

import java.time.OffsetDateTime;

/**
 * ScheduledPost.user and .achievement are both already @JsonIgnore on the
 * entity, so serializing it directly wouldn't throw — but that also silently
 * drops which achievement a post came from, which a real data export
 * shouldn't lose. DTO instead, with achievementId as a plain scalar.
 */
public record ScheduledPostExportResponse(
        Long id,
        Long achievementId,
        PostPlatform platform,
        String body,
        PostStatus status,
        OffsetDateTime scheduledFor,
        String userTimezone,
        OffsetDateTime postedAt,
        String externalPostId,
        OffsetDateTime createdAt
) {
    public static ScheduledPostExportResponse from(ScheduledPost p) {
        return new ScheduledPostExportResponse(
                p.getId(),
                p.getAchievement() == null ? null : p.getAchievement().getId(),
                p.getPlatform(),
                p.getBody(),
                p.getStatus(),
                p.getScheduledFor(),
                p.getUserTimezone(),
                p.getPostedAt(),
                p.getExternalPostId(),
                p.getCreatedAt()
        );
    }
}
