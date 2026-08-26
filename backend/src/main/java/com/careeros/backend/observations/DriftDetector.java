package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * A repository's primary language (the one field GitHub gives us for a repo,
 * stored — commit-level file lists aren't persisted, only fetched live
 * during analysis and discarded) is active in recent commits but has never
 * shown up in any achievement's technologiesJson.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriftDetector implements ObservationDetector {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final AchievementRepository achievementRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.observations.drift.weeks:8}")
    private int windowWeeks;

    @Value("${app.observations.drift.max-repos:3}")
    private int maxRepos;

    private record Candidate(GithubRepository repository, long commitCount) {
    }

    @Override
    public List<Observation> detect(User user) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(windowWeeks);
        TreeSet<String> knownTechnologies = technologiesEverUsed(user);

        return githubRepositoryRepository.findByUser(user).stream()
                .filter(r -> r.getLanguage() != null && !r.getLanguage().isBlank())
                .filter(r -> !knownTechnologies.contains(r.getLanguage().toLowerCase(Locale.ROOT)))
                .map(r -> new Candidate(r, githubCommitRepository.countByRepositoryAndCommittedAtAfter(r, since)))
                .filter(c -> c.commitCount() > 0)
                .sorted(Comparator.comparingLong(Candidate::commitCount).reversed())
                .limit(maxRepos)
                .map(c -> toObservation(c, windowWeeks))
                .toList();
    }

    private TreeSet<String> technologiesEverUsed(User user) {
        TreeSet<String> technologies = new TreeSet<>();
        for (AchievementEntity achievement : achievementRepository.findByUser(user)) {
            String json = achievement.getTechnologiesJson();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                for (String technology : objectMapper.readValue(json, new TypeReference<List<String>>() {})) {
                    technologies.add(technology.toLowerCase(Locale.ROOT));
                }
            } catch (Exception e) {
                log.warn("Failed to parse technologiesJson for achievement {}, skipping it", achievement.getId(), e);
            }
        }
        return technologies;
    }

    private Observation toObservation(Candidate c, int weeks) {
        String statement = "%s has %d commit%s in the last %d weeks written in %s. %s has never appeared in any of your achievements."
                .formatted(c.repository().getFullName(), c.commitCount(), c.commitCount() == 1 ? "" : "s",
                        weeks, c.repository().getLanguage(), c.repository().getLanguage());

        List<String> evidence = List.of(
                "%s commits in the last %d weeks".formatted(c.commitCount(), weeks),
                "Repository language: " + c.repository().getLanguage());

        return new Observation(ObservationType.DRIFT, statement, evidence,
                "Analyze " + c.repository().getFullName() + " — that work isn't represented yet.");
    }
}
