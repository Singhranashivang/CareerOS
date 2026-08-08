package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Scores how much source material backed an achievement.
 *
 * This replaces the LLM's self-reported confidence, which was constant at
 * 0.95 for every row — a model grading its own output can't rank anything.
 * Here the number means something checkable: how many independent kinds of
 * evidence the claim rests on.
 */
@Component
public class AchievementConfidenceCalculator {

    public double calculate(Evidence evidence) {

        if (evidence == null) {
            return 0d;
        }

        double score = 0;

        if (notEmpty(evidence.getFeatures()))              score += 0.30;
        if (notEmpty(evidence.getPullRequestTitles()))     score += 0.20;
        if (notEmpty(evidence.getDependencies()))          score += 0.15;
        if (notEmpty(evidence.getRepositoryFeatures()))    score += 0.15;
        if (notEmpty(evidence.getChangedFileInsights()))   score += 0.10;
        if (hasText(evidence.getReadme()))                 score += 0.10;

        return Math.min(score, 1.0);
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}