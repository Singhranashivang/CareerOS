package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.engine.AchievementType;
import com.careeros.backend.achievement.record.AchievementSource;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AchievementTimelineResponse {

    private Long id;

    private Long repositoryId;
    private String repository;

    private AchievementSource source;
    private AchievementType type;

    private String title;
    private String summary;

    private String resumeBullet;

    private String starSituation;
    private String starTask;
    private String starAction;
    private String starResult;

    private double confidence;

    private LocalDateTime generatedAt;
}