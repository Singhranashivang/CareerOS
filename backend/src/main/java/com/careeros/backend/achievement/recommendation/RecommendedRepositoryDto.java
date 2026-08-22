package com.careeros.backend.achievement.recommendation;

import lombok.Builder;
import lombok.Getter;

/**
 * What the frontend needs to know about a recommended repository. No
 * entity reference — {@link RepositoryRecommendation} (the internal,
 * entity-holding value object used by service-layer callers like
 * OnboardingService and WeeklyAchievementService) must never cross an HTTP
 * boundary directly; this is the DTO that replaces it at that boundary.
 */
@Getter
@Builder
public class RecommendedRepositoryDto {

    private Long id;

    private String name;

    private int commitCount;

    private int score;
}
