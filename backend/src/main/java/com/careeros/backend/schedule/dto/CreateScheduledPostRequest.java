package com.careeros.backend.schedule.dto;

import com.careeros.backend.schedule.PostPlatform;
import com.careeros.backend.schedule.PostStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * body may be null when achievementId is set — the achievement's resume bullet
 * is copied in, so the user edits rather than writes from scratch.
 */
public record CreateScheduledPostRequest(
        @NotNull PostPlatform platform,
        String body,
        PostStatus status,
        OffsetDateTime scheduledFor,
        String userTimezone,
        Long achievementId
) {
}
