package com.careeros.backend.onboarding.dto;

import com.careeros.backend.onboarding.OnboardingRun;
import com.careeros.backend.onboarding.OnboardingStage;
import com.careeros.backend.onboarding.OnboardingStatus;

import java.time.OffsetDateTime;

public record OnboardingRunResponse(
        Long id,
        OnboardingStatus status,
        OnboardingStage stage,
        int reposFound,
        int commitsSynced,
        int prReposSynced,
        int reposToAnalyze,
        int reposAnalyzed,
        int achievementsCreated,
        String currentRepositoryName,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static OnboardingRunResponse from(OnboardingRun r) {
        return new OnboardingRunResponse(
                r.getId(),
                r.getStatus(),
                r.getStage(),
                r.getReposFound(),
                r.getCommitsSynced(),
                r.getPrReposSynced(),
                r.getReposToAnalyze(),
                r.getReposAnalyzed(),
                r.getAchievementsCreated(),
                r.getCurrentRepositoryName(),
                r.getErrorMessage(),
                r.getStartedAt(),
                r.getCompletedAt()
        );
    }
}
