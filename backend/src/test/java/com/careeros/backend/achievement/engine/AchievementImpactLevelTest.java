package com.careeros.backend.achievement.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementImpactLevelTest {

    @Test
    void boundariesMatchTheFrontendsThresholds() {
        assertThat(AchievementImpactLevel.of(0.7)).isEqualTo(AchievementImpactLevel.HIGH_IMPACT);
        assertThat(AchievementImpactLevel.of(0.69)).isEqualTo(AchievementImpactLevel.FOUNDATIONAL);
        assertThat(AchievementImpactLevel.of(0.5)).isEqualTo(AchievementImpactLevel.FOUNDATIONAL);
        assertThat(AchievementImpactLevel.of(0.49)).isEqualTo(AchievementImpactLevel.INSUFFICIENT);
        assertThat(AchievementImpactLevel.of(1.0)).isEqualTo(AchievementImpactLevel.HIGH_IMPACT);
        assertThat(AchievementImpactLevel.of(0.0)).isEqualTo(AchievementImpactLevel.INSUFFICIENT);
    }
}
