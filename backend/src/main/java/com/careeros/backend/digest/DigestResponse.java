package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;

import java.util.List;

/** summary is null when the user has never had a weekly run yet. */
public record DigestResponse(
        DigestSummaryResponse summary,
        List<AchievementTimelineResponse> achievements
) {
}
