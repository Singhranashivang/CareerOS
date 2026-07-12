package com.careeros.backend.achievement.engine;

public interface AchievementEngine {

    AchievementResult generate(
            AchievementInput input
    );

}