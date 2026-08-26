package com.careeros.backend.suggestions;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/suggestions — what to act on now, not what exists. suggestions is
 * already sorted best-first. daysSinceLastPost/lastPostedAt are both null
 * when the user has never posted.
 */
public record SuggestionsResponse(
        List<SuggestedAchievementResponse> suggestions,
        Long daysSinceLastPost,
        OffsetDateTime lastPostedAt,
        List<String> technologiesNotYetPosted,
        List<String> repositoriesWithNoAchievements
) {
}
