package com.careeros.backend.achievement.recommendation;

import org.springframework.stereotype.Component;

@Component
public class RepositoryScoreCalculator {

    public int calculate(
            int commits,
            int pullRequests,
            int technologies,
            int features,
            boolean hasReadme,
            boolean hasDescription
    ) {

        int score = 0;

        score += commits * 3;
        score += pullRequests * 5;
        score += technologies * 4;
        score += features * 8;

        if (hasReadme) {
            score += 15;
        }

        if (hasDescription) {
            score += 10;
        }

        return score;
    }
}