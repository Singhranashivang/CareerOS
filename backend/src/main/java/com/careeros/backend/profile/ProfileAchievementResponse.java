package com.careeros.backend.profile;

import com.careeros.backend.achievement.engine.AchievementImpactLevel;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileAchievementResponse(
        Long id,
        String title,
        String resumeBullet,
        String repositoryName,
        LocalDateTime generatedAt,
        double confidence,
        AchievementImpactLevel impactLevel,
        List<String> citedCommitShas
) {
}
