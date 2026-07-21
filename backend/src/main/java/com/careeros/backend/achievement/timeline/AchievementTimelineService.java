package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementTimelineService {

    private final AchievementRepository achievementRepository;

    public List<AchievementTimelineResponse> timeline(User user) {

        return achievementRepository.findByUser(user)
                .stream()
                .sorted(
                        Comparator.comparing(
                                AchievementEntity::getGeneratedAt
                        ).reversed()
                )
                .map(this::toResponse)
                .toList();
    }

    private AchievementTimelineResponse toResponse(
            AchievementEntity entity
    ) {

        return AchievementTimelineResponse.builder()
                .id(entity.getId())
                .repository(entity.getRepository())
                .type(entity.getType())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .confidence(entity.getConfidence())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }

}
