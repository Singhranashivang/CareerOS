package com.careeros.backend.user;

/**
 * Asked once during onboarding (OnboardingController.start), changeable
 * afterward via PATCH /api/me/goal. Null until the user answers — every
 * consumer of this field (AchievementPromptBuilder, LinkedInPromptBuilder,
 * SuggestionScorer, AchievementTimelineService, SilenceDetector) treats null
 * as "no goal set yet" and falls back to today's goal-neutral behaviour.
 */
public enum UserGoal {

    /** Favours breadth of technology and resume-shaped bullets in generation; ranking favours technology breadth. */
    JOB_HUNTING,

    /** Favours narrative and surprising detail in generation; ranking pushes posting harder. */
    AUDIENCE_BUILDING,

    /** Favours impact and scope in generation; ranking favours confidence (scope/magnitude), timeline groups by quarter. */
    PERFORMANCE_REVIEW
}
