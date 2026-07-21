package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.engine.AchievementType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AchievementTimelineResponse {

    private Long id;

    private String repository;

    private AchievementType type;

    private String title;

    private String summary;

    private double confidence;

    private LocalDateTime generatedAt;

}
