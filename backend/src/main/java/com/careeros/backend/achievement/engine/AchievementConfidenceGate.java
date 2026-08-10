package com.careeros.backend.achievement.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One threshold, shared by every path that persists something scored by
 * {@link AchievementConfidenceCalculator} — the per-repo generator, the
 * weekly per-repo achievement, and the weekly summary. Centralised so the
 * default and its formatting stay in one place rather than three.
 */
@Component
public class AchievementConfidenceGate {

    @Value("${app.achievement.min-confidence:0.5}")
    private double minConfidence;

    public double minConfidence() {
        return minConfidence;
    }

    public boolean passes(double confidence) {
        return confidence >= minConfidence;
    }

    public String reasonBelowThreshold(double confidence) {
        return String.format(
                "Computed confidence %.2f is below the %.2f threshold",
                confidence, minConfidence);
    }
}
