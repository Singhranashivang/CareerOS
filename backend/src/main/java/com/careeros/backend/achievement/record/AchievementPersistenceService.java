package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.achievement.llm.BannedVocabularyScrubber;
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
    private final DismissedClusterSignalRepository dismissedClusterSignalRepository;

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

            scrubBannedVocabulary(entity);
            achievementRepository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save achievement", e);
        }
    }

    /** Used by the generator, which has the full repository entity. */
    public AchievementEntity saveEntity(AchievementEntity entity) {
        scrubBannedVocabulary(entity);
        return achievementRepository.save(entity);
    }

    /**
     * Both save paths above are the only two places an achievement is ever
     * persisted (see the grep that confirmed it) — scrubbing here, not in
     * the generator or the weekly pipeline, covers every caller once. Same
     * deterministic substitution the LinkedIn post path uses, applied
     * before this text can seed a LinkedIn prompt's "evidence" and get
     * copied forward — see LinkedInPromptBuilder's SUBSTANCE section, which
     * already warns the model that evidence "may itself contain the banned
     * words above." Not applied in edit() — a user's own edit is their own
     * voice, not a generation this scrubber is meant to clean up.
     */
    private static void scrubBannedVocabulary(AchievementEntity entity) {
        entity.setTitle(scrubOrKeepOriginal(entity.getTitle()));
        entity.setResumeBullet(scrubOrKeepOriginal(entity.getResumeBullet()));
        entity.setSummary(scrubOrKeepOriginal(entity.getSummary()));
        entity.setStarSituation(scrubOrKeepOriginal(entity.getStarSituation()));
        entity.setStarTask(scrubOrKeepOriginal(entity.getStarTask()));
        entity.setStarAction(scrubOrKeepOriginal(entity.getStarAction()));
        entity.setStarResult(scrubOrKeepOriginal(entity.getStarResult()));
    }

    /**
     * The scrubber can drop an entire sentence (see BannedVocabularyScrubber's
     * unknown-inflection fallback, e.g. "enhancement") — fine for a
     * multi-sentence LinkedIn post, but a short field like title is
     * sometimes exactly one "sentence" with no terminating period, so that
     * fallback can blank the whole field. A banned word left in a title is
     * still better than an empty title, so keep the original in that case
     * rather than persist blank text.
     */
    private static String scrubOrKeepOriginal(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String scrubbed = BannedVocabularyScrubber.scrub(text);
        return scrubbed.isBlank() ? text : scrubbed;
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

    /**
     * Excluded from lists and never regenerated from here on — see
     * AchievementEntity.dismissed. Also records what this cluster touched
     * (technologies, file paths) so dismissing teaches the generator
     * something instead of only hiding one row — see
     * DismissedAreaOverlapGate, checked before a new cluster is generated.
     */
    public AchievementEntity dismiss(User user, Long id) {
        AchievementEntity entity = requireOwned(user, id);
        entity.setDismissed(true);
        recordDismissalSignal(entity);
        return entity;
    }

    private void recordDismissalSignal(AchievementEntity entity) {
        dismissedClusterSignalRepository.save(DismissedClusterSignal.builder()
                .user(entity.getUser())
                .repositoryName(entity.getRepositoryName())
                .technologiesJson(entity.getTechnologiesJson())
                .filePathsJson(entity.getFilePathsJson())
                .dismissedAt(LocalDateTime.now())
                .build());
    }

    private AchievementEntity requireOwned(User user, Long id) {
        return achievementRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new AccessDeniedException("Achievement not found"));
    }
}