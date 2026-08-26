package com.careeros.backend.suggestions;

import java.time.LocalDateTime;

/** One ranked, unposted achievement — reason is the human-readable "why here", score is what it was sorted by. */
public record SuggestedAchievementResponse(
        Long achievementId,
        String title,
        String repositoryName,
        double confidence,
        LocalDateTime generatedAt,
        double score,
        String reason
) {
}
