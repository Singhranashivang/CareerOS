package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AchievementPersistenceService {

    private final AchievementRepository achievementRepository;

    private final ObjectMapper objectMapper;

    /**
     * Saves an achievement produced by the weekly pipeline, which knows the
     * repository only by name. repository stays null in that case.
     */
    public void save(User user, Achievement achievement) {

        try {

            AchievementEntity entity = AchievementEntity.builder()
                    .user(user)
                    .repositoryName(achievement.getRepository())
                    .source(AchievementSource.GITHUB)
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

            log.debug("Saving achievement '{}' for repository {} (user {})",
                    achievement.getTitle(),
                    achievement.getRepository(),
                    user.getId());

            achievementRepository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save achievement", e);
        }
    }

    /** Used by the generator, which has the full repository entity. */
    public AchievementEntity saveEntity(AchievementEntity entity) {
        return achievementRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AchievementEntity> findByUser(User user) {
        return achievementRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public List<AchievementEntity> findByUserNewestFirst(User user) {
        return achievementRepository.findByUserOrderByGeneratedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public long countForUser(User user) {
        return achievementRepository.countByUser(user);
    }
}