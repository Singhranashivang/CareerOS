package com.careeros.backend.digest;

import java.time.LocalDateTime;

public record DigestSummaryResponse(
        LocalDateTime lastRunAt,
        WeeklyDigestOutcome outcome,
        String reason,
        int reposSynced,
        int commitsSynced,
        int reposAnalyzed,
        int achievementsCreated
) {
    public static DigestSummaryResponse from(WeeklyDigestRun run) {
        return new DigestSummaryResponse(
                run.getRunAt(), run.getOutcome(), run.getReason(),
                run.getReposSynced(), run.getCommitsSynced(),
                run.getReposAnalyzed(), run.getAchievementsCreated());
    }
}
