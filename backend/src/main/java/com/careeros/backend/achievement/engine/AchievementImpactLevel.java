package com.careeros.backend.achievement.engine;

/**
 * Same three tiers and thresholds the frontend already derives client-side
 * (WeeklyPage's impactLevel()) — computed here too so the profile endpoint
 * can report it without duplicating the cutoffs a second time.
 */
public enum AchievementImpactLevel {

    HIGH_IMPACT,
    FOUNDATIONAL,
    INSUFFICIENT;

    public static AchievementImpactLevel of(double confidence) {
        if (confidence >= 0.7) {
            return HIGH_IMPACT;
        }
        if (confidence >= 0.5) {
            return FOUNDATIONAL;
        }
        return INSUFFICIENT;
    }
}
