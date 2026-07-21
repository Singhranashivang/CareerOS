package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

@Component
public class AchievementConfidenceCalculator {

    public double calculate(Evidence evidence) {

        double score = 0;

        if (!evidence.getFeatures().isEmpty())
            score += 0.30;

        if (!evidence.getPullRequestTitles().isEmpty())
            score += 0.20;

        if (!evidence.getDependencies().isEmpty())
            score += 0.15;

        if (!evidence.getRepositoryFeatures().isEmpty())
            score += 0.15;

        if (!evidence.getChangedFileInsights().isEmpty())
            score += 0.10;

        if (!evidence.getReadme().isBlank())
            score += 0.10;

        return Math.min(score, 1.0);
    }

}