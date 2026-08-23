package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public long countForUser(User user) {
        return achievementRepository.countByUser(user);
    }

    /**
     * Full replace of the six editable fields, not a partial patch — see
     * AchievementEditRequest. Mutates the managed entity and relies on
     * Hibernate's dirty checking to persist it at commit, same as
     * OnboardingRunService's per-field updates.
     */
    public AchievementEntity edit(User user, Long id, AchievementEditRequest request) {

        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }

        AchievementEntity entity = requireOwned(user, id);

        entity.setTitle(request.title());
        entity.setResumeBullet(request.resumeBullet());
        entity.setStarSituation(request.starSituation());
        entity.setStarTask(request.starTask());
        entity.setStarAction(request.starAction());
        entity.setStarResult(request.starResult());
        entity.setUserEdited(true);

        return entity;
    }

    /** Excluded from lists and never regenerated from here on — see AchievementEntity.dismissed. */
    public AchievementEntity dismiss(User user, Long id) {
        AchievementEntity entity = requireOwned(user, id);
        entity.setDismissed(true);
        return entity;
    }

    private AchievementEntity requireOwned(User user, Long id) {
        return achievementRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new AccessDeniedException("Achievement not found"));
    }
}