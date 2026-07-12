package com.careeros.backend.achievement.engine;

import org.springframework.stereotype.Component;

@Component
public class RuleBasedAchievementEngine implements AchievementEngine {

    @Override
    public AchievementResult generate(
            AchievementInput input
    ) {

        return AchievementResult.builder()
                .title("Worked on " + input.getRepository().getName())
                .description(
                        "Completed "
                                + input.getCommits().size()
                                + " commits."
                )
                .confidence(0.70)
                .build();
    }
}