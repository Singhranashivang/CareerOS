package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Scores how much source material backed an achievement.
 *
 * This replaces the LLM's self-reported confidence, which was constant at
 * 0.95 for every row — a model grading its own output can't rank anything.
 *
 * Half the original six components here (dependencies, repositoryFeatures,
 * readme) turned out to be repository-scoped, not cluster-scoped — the exact
 * same value for every cluster from the same repository regardless of size.
 * Two more (features, changedFileInsights) are cluster-scoped but only check
 * non-emptiness: real data showed any cluster with at least one real commit
 * always produces at least one of each, so those saturated to "true" just as
 * constantly. Recomputing against real achievements from the same
 * repository — clusters of 1 commit and of 8 — every one of those five
 * booleans came out identical regardless of cluster size; only magnitude
 * (below) actually varied. The calculator was, in effect, a per-repository
 * constant.
 */
@Component
public class AchievementConfidenceCalculator {

    /** How much of the score CodeStats' actual size of the cluster's change accounts for. */
    @Value("${app.achievement.confidence.magnitude-weight:0.30}")
    private double magnitudeWeight;

    /** Authored lines changed (added+deleted) at or above this counts as a "large" change — log-scaled below it. */
    @Value("${app.achievement.confidence.large-change-lines:5000}")
    private int largeChangeLines;

    /** Files touched at or above this counts as "large" — same reasoning as largeChangeLines. */
    @Value("${app.achievement.confidence.large-change-files:100}")
    private int largeChangeFiles;

    public double calculate(Evidence evidence) {

        if (evidence == null) {
            return 0d;
        }

        double score = 0;

        if (notEmpty(evidence.getFeatures()))              score += 0.20;
        if (notEmpty(evidence.getPullRequestTitles()))     score += 0.15;
        if (notEmpty(evidence.getDependencies()))          score += 0.10;
        if (notEmpty(evidence.getRepositoryFeatures()))    score += 0.10;
        if (notEmpty(evidence.getChangedFileInsights()))   score += 0.10;
        if (hasText(evidence.getReadme()))                 score += 0.05;

        score += magnitudeWeight * magnitude(evidence.getCodeStats());

        return Math.min(score, 1.0);
    }

    /**
     * Log-scaled, not linear: this dataset's real clusters span 165 to
     * 61,656 authored lines and 4 to 376 files touched — a linear cap low
     * enough to separate the small end saturates everything past a few
     * hundred lines to 1.0, which is the exact loss of discrimination this
     * replaced. Log keeps a 165-line tweak and a 61k-line refactor visibly
     * apart instead of both reading as "large."
     */
    private double magnitude(CodeStats codeStats) {
        if (codeStats == null) {
            return 0;
        }
        double lineScore = logScale(codeStats.getLinesAdded() + codeStats.getLinesDeleted(), largeChangeLines);
        double fileScore = logScale(codeStats.getFilesTouched(), largeChangeFiles);
        return (lineScore + fileScore) / 2.0;
    }

    private static double logScale(int value, int ceiling) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(1.0, Math.log1p(value) / Math.log1p(ceiling));
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}