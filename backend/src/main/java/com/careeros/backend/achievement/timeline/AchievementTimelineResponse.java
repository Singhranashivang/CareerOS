package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.engine.AchievementType;
import com.careeros.backend.achievement.record.AchievementEntity;
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

    private boolean userEdited;
    private boolean dismissed;

    public static AchievementTimelineResponse from(AchievementEntity entity) {
        return AchievementTimelineResponse.builder()
                .id(entity.getId())
                .repositoryId(entity.getRepository() == null
                        ? null : entity.getRepository().getId())
                .repository(entity.getRepositoryName())
                .source(entity.getSource())
                .type(entity.getType())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .resumeBullet(entity.getResumeBullet())
                .starSituation(entity.getStarSituation())
                .starTask(entity.getStarTask())
                .starAction(entity.getStarAction())
                .starResult(entity.getStarResult())
                .confidence(entity.getConfidence())
                .generatedAt(entity.getGeneratedAt())
                .userEdited(entity.isUserEdited())
                .dismissed(entity.isDismissed())
                .build();
    }
}