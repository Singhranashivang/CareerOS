package com.careeros.backend.onboarding;

/** Ordered as the run actually progresses through them. */
public enum OnboardingStage {
    CONNECTING,
    REPOS_FOUND,
    SYNCING_COMMITS,
    SYNCING_PULL_REQUESTS,
    ANALYZING,
    DONE
}
