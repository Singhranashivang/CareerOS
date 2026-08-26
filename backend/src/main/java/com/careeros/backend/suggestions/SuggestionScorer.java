package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.user.UserGoal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranks one unposted achievement — confidence, recency, repository momentum,
 * and (goal-dependent) technology breadth. Weights are tunable, same as
 * every other generator constant in this app (see application.properties).
 */
@Component
@RequiredArgsConstructor
public class SuggestionScorer {

    private final ObjectMapper objectMapper;

    @Value("${app.suggestions.weight.confidence:0.5}")
    private double confidenceWeight;

    @Value("${app.suggestions.weight.recency:0.3}")
    private double recencyWeight;

    @Value("${app.suggestions.weight.repo-momentum:0.2}")
    private double repoMomentumWeight;

    /** JOB_HUNTING only: weight for how many distinct technologies the achievement spans. */
    @Value("${app.suggestions.weight.tech-breadth:0.3}")
    private double techBreadthWeight;

    /** Distinct technology count at or above this scores the full techBreadthWeight — log-scaled below it. */
    @Value("${app.suggestions.tech-breadth.ceiling:5}")
    private int techBreadthCeiling;

    /** AUDIENCE_BUILDING only: added on top of recencyWeight — pushes the freshest work to post about harder. */
    @Value("${app.suggestions.goal.audience-building.recency-boost:0.3}")
    private double audienceBuildingRecencyBoost;

    /** PERFORMANCE_REVIEW only: added on top of confidenceWeight — confidence already tracks change magnitude (see AchievementConfidenceCalculator). */
    @Value("${app.suggestions.goal.performance-review.confidence-boost:0.3}")
    private double performanceReviewConfidenceBoost;

    /** Confidence >= this is called out by name in the reason, not just folded into the score. */
    @Value("${app.suggestions.high-confidence-threshold:0.75}")
    private double highConfidenceThreshold;

    /** Generated within this many days is called out as "recent" in the reason. */
    @Value("${app.suggestions.recent-days:7}")
    private int recentDays;

    public record Scored(double score, String reason) {
    }

    public Scored score(AchievementEntity achievement, boolean repositoryHasPostedWork) {
        return score(achievement, repositoryHasPostedWork, null);
    }

    /**
     * goal is the ranking user's UserGoal (User.goal) — null (not set yet)
     * scores exactly as before this parameter existed. See the per-goal
     * @Value fields above for exactly what each goal changes about the
     * weighting.
     */
    public Scored score(AchievementEntity achievement, boolean repositoryHasPostedWork, UserGoal goal) {

        double confidence = achievement.getConfidence();
        long daysSinceGenerated = achievement.getGeneratedAt() == null
                ? Long.MAX_VALUE / 2 // no date on record — treat as arbitrarily old, never "recent"
                : Duration.between(achievement.getGeneratedAt(), LocalDateTime.now()).toDays();

        // Bounded (0, 1], newest always higher, but never so steep that a
        // 30-day-old high-confidence achievement loses to a 1-day-old thin one.
        double recencyScore = 1.0 / (1 + daysSinceGenerated);
        double repoMomentumScore = repositoryHasPostedWork ? 1.0 : 0.0;
        int technologyCount = technologyCount(achievement);

        double effectiveConfidenceWeight = confidenceWeight;
        double effectiveRecencyWeight = recencyWeight;
        double effectiveTechBreadthWeight = 0;

        if (goal == UserGoal.JOB_HUNTING) {
            effectiveTechBreadthWeight = techBreadthWeight;
        } else if (goal == UserGoal.AUDIENCE_BUILDING) {
            effectiveRecencyWeight += audienceBuildingRecencyBoost;
        } else if (goal == UserGoal.PERFORMANCE_REVIEW) {
            effectiveConfidenceWeight += performanceReviewConfidenceBoost;
        }

        double total = effectiveConfidenceWeight * confidence
                + effectiveRecencyWeight * recencyScore
                + repoMomentumWeight * repoMomentumScore
                + effectiveTechBreadthWeight * logScale(technologyCount, techBreadthCeiling);

        return new Scored(total,
                reason(confidence, daysSinceGenerated, repositoryHasPostedWork, goal, technologyCount));
    }

    private int technologyCount(AchievementEntity achievement) {
        String json = achievement.getTechnologiesJson();
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {}).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private static double logScale(int value, int ceiling) {
        if (value <= 0 || ceiling <= 0) {
            return 0;
        }
        return Math.min(1.0, Math.log1p(value) / Math.log1p(ceiling));
    }

    private String reason(
            double confidence, long daysSinceGenerated, boolean repositoryHasPostedWork,
            UserGoal goal, int technologyCount
    ) {

        List<String> reasons = new ArrayList<>();

        if (confidence >= highConfidenceThreshold) {
            reasons.add("high confidence (%.0f%%)".formatted(confidence * 100));
        }
        if (daysSinceGenerated <= recentDays) {
            reasons.add(daysSinceGenerated == 0
                    ? "generated today"
                    : "generated %d day%s ago".formatted(daysSinceGenerated, daysSinceGenerated == 1 ? "" : "s"));
        }
        if (repositoryHasPostedWork) {
            reasons.add("this repository already has posted work");
        }
        if (goal == UserGoal.JOB_HUNTING && technologyCount > 1) {
            reasons.add("spans %d technologies".formatted(technologyCount));
        }

        if (reasons.isEmpty()) {
            return "Ranked by confidence, recency, and repository activity relative to the rest of the list";
        }

        String joined = String.join("; ", reasons);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }
}
