package com.careeros.backend.account;

import com.careeros.backend.account.dto.AuditLogExportResponse;
import com.careeros.backend.account.dto.CommitExportResponse;
import com.careeros.backend.account.dto.ScheduledPostExportResponse;
import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.github.dto.RepositoryResponse;

import java.time.LocalDateTime;
import java.util.List;

/** GET /api/account/export — everything stored about the user, in one document. */
public record AccountExportResponse(
        LocalDateTime exportedAt,
        List<AchievementTimelineResponse> achievements,
        List<RepositoryResponse> repositories,
        List<CommitExportResponse> commits,
        List<ScheduledPostExportResponse> scheduledPosts,
        List<AuditLogExportResponse> auditLog
) {
}
