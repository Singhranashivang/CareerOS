package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementTimelineService {

    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementTimelineResponse> timeline(User user) {

        return achievementRepository.findByUserOrderByGeneratedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AchievementTimelineResponse toResponse(AchievementEntity entity) {

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
                .build();
    }
}