package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranks one unposted achievement — confidence, recency, and whether its
 * repository already has posted work (an easy next post beats starting a
 * new repository's story). Weights are tunable, same as every other
 * generator constant in this app (see application.properties).
 */
@Component
public class SuggestionScorer {

    @Value("${app.suggestions.weight.confidence:0.5}")
    private double confidenceWeight;

    @Value("${app.suggestions.weight.recency:0.3}")
    private double recencyWeight;

    @Value("${app.suggestions.weight.repo-momentum:0.2}")
    private double repoMomentumWeight;

    /** Confidence >= this is called out by name in the reason, not just folded into the score. */
    @Value("${app.suggestions.high-confidence-threshold:0.75}")
    private double highConfidenceThreshold;

    /** Generated within this many days is called out as "recent" in the reason. */
    @Value("${app.suggestions.recent-days:7}")
    private int recentDays;

    public record Scored(double score, String reason) {
    }

    public Scored score(AchievementEntity achievement, boolean repositoryHasPostedWork) {

        double confidence = achievement.getConfidence();
        long daysSinceGenerated = achievement.getGeneratedAt() == null
                ? Long.MAX_VALUE / 2 // no date on record — treat as arbitrarily old, never "recent"
                : Duration.between(achievement.getGeneratedAt(), LocalDateTime.now()).toDays();

        // Bounded (0, 1], newest always higher, but never so steep that a
        // 30-day-old high-confidence achievement loses to a 1-day-old thin one.
        double recencyScore = 1.0 / (1 + daysSinceGenerated);
        double repoMomentumScore = repositoryHasPostedWork ? 1.0 : 0.0;

        double total = confidenceWeight * confidence
                + recencyWeight * recencyScore
                + repoMomentumWeight * repoMomentumScore;

        return new Scored(total, reason(confidence, daysSinceGenerated, repositoryHasPostedWork));
    }

    private String reason(double confidence, long daysSinceGenerated, boolean repositoryHasPostedWork) {

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

        if (reasons.isEmpty()) {
            return "Ranked by confidence, recency, and repository activity relative to the rest of the list";
        }

        String joined = String.join("; ", reasons);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }
}
