package com.careeros.backend.profile;

import com.careeros.backend.achievement.engine.AchievementImpactLevel;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AchievementRepository achievementRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(User user) {

        List<AchievementEntity> achievements =
                achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user);

        List<ProfileAchievementResponse> items = achievements.stream()
                .map(this::toResponse)
                .toList();

        long repositoriesContributedTo = achievements.stream()
                .map(AchievementEntity::getRepositoryName)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        List<LocalDateTime> dates = achievements.stream()
                .map(AchievementEntity::getGeneratedAt)
                .filter(Objects::nonNull)
                .toList();

        List<String> technologies = achievements.stream()
                .flatMap(a -> parseStringList(a.getTechnologiesJson()).stream())
                .distinct()
                .sorted()
                .toList();

        ProfileTotalsResponse totals = new ProfileTotalsResponse(
                achievements.size(),
                (int) repositoriesContributedTo,
                dates.stream().min(Comparator.naturalOrder()).orElse(null),
                dates.stream().max(Comparator.naturalOrder()).orElse(null),
                technologies);

        return new ProfileResponse(items, totals);
    }

    /**
     * Résumé-bullet list, grouped by repository in the order each repository
     * first appears (achievements are already newest-first, so that's also
     * the order of most-recently-active repository first). No STAR fields —
     * this is meant to be pasted straight into a CV.
     */
    @Transactional(readOnly = true)
    public String exportMarkdown(User user) {

        List<AchievementEntity> achievements =
                achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user);

        StringBuilder markdown = new StringBuilder("# Career Profile\n\n");

        if (achievements.isEmpty()) {
            markdown.append("No achievements yet.\n");
            return markdown.toString();
        }

        List<LocalDateTime> dates = achievements.stream()
                .map(AchievementEntity::getGeneratedAt)
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime start = dates.stream().min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime end = dates.stream().max(Comparator.naturalOrder()).orElse(null);
        if (start != null && end != null) {
            markdown.append("**").append(HEADER_DATE.format(start))
                    .append(" – ").append(HEADER_DATE.format(end)).append("**\n\n");
        }

        Map<String, List<AchievementEntity>> byRepository = new LinkedHashMap<>();
        for (AchievementEntity achievement : achievements) {
            String repositoryName = achievement.getRepositoryName() == null
                    ? "Other" : achievement.getRepositoryName();
            byRepository.computeIfAbsent(repositoryName, k -> new ArrayList<>()).add(achievement);
        }

        for (var entry : byRepository.entrySet()) {
            markdown.append("## ").append(entry.getKey()).append("\n\n");
            for (AchievementEntity achievement : entry.getValue()) {
                if (achievement.getResumeBullet() != null && !achievement.getResumeBullet().isBlank()) {
                    markdown.append("- ").append(achievement.getResumeBullet()).append("\n");
                }
            }
            markdown.append("\n");
        }

        return markdown.toString();
    }

    private ProfileAchievementResponse toResponse(AchievementEntity entity) {
        return new ProfileAchievementResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getResumeBullet(),
                entity.getRepositoryName(),
                entity.getGeneratedAt(),
                entity.getConfidence(),
                AchievementImpactLevel.of(entity.getConfidence()),
                parseStringList(entity.getCitedCommitShasJson()));
    }

    /** citedCommitShasJson/technologiesJson are both plain JSON string arrays; null/blank reads as empty. */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Could not parse stored JSON string list '{}', treating as empty", json, e);
            return List.of();
        }
    }
}
