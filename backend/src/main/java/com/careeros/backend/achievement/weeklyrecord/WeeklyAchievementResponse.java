package com.careeros.backend.achievement.weeklyrecord;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO for {@link WeeklyAchievementEntity} — same convention as
 * AchievementTimelineResponse: no entity reference, and the raw JSON blob
 * columns are omitted rather than exposed as opaque strings (nothing reads
 * them today; add parsed highlights/technologies fields if a consumer needs
 * them).
 */
@Getter
@Builder
public class WeeklyAchievementResponse {

    private Long id;

    private String title;

    private String summary;

    private Double confidence;

    private LocalDateTime generatedAt;

    public static WeeklyAchievementResponse from(WeeklyAchievementEntity entity) {
        return WeeklyAchievementResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .confidence(entity.getConfidence())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
