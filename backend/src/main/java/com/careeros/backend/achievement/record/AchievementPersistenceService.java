package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementPersistenceService {

    private final AchievementRepository achievementRepository;

    private final ObjectMapper objectMapper;

    public void save(User user, Achievement achievement) {

        try {

            AchievementEntity entity = AchievementEntity.builder()
                    .user(user)
                    .repository(achievement.getRepository())
                    .type(achievement.getType())
                    .title(achievement.getTitle())
                    .summary(achievement.getSummary())
                    .evidenceJson(
                            objectMapper.writeValueAsString(
                                    achievement.getEvidence()
                            )
                    )
                    .technologiesJson(
                            objectMapper.writeValueAsString(
                                    achievement.getTechnologies()
                            )
                    )
                    .confidence(achievement.getConfidence())
                    .generatedAt(LocalDateTime.now())
                    .build();

            System.out.println("Saving achievement:");
            System.out.println("Title      = " + achievement.getTitle());
            System.out.println("Repository = " + achievement.getRepository());
            System.out.println("User ID    = " + user.getId());

            achievementRepository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save achievement", e);
        }

    }

    public List<AchievementEntity> findByUser(User user) {
        return achievementRepository.findByUser(user);
    }

    public long count() {
        return achievementRepository.count();
    }

}