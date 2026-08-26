package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The user's last K achievements are all from one repository while another
 * repository has real, un-credited activity in the same window. There's no
 * frontend/backend or domain label anywhere in the schema, so "area" is
 * grounded in repositoryName — the same denormalised field every other
 * achievement query already keys off.
 */
@Component
@RequiredArgsConstructor
public class ConcentrationDetector implements ObservationDetector {

    private final AchievementRepository achievementRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;

    @Value("${app.observations.concentration.window:6}")
    private int window;

    @Override
    public List<Observation> detect(User user) {
        List<AchievementEntity> recent = achievementRepository
                .findByUserAndDismissedFalseOrderByGeneratedAtDesc(user);

        if (recent.size() < window) {
            return List.of();
        }

        List<AchievementEntity> lastK = recent.subList(0, window);
        String dominantRepo = lastK.get(0).getRepositoryName();
        boolean allSameRepo = dominantRepo != null && lastK.stream()
                .allMatch(a -> dominantRepo.equals(a.getRepositoryName()));

        if (!allSameRepo) {
            return List.of();
        }

        var oldestOfK = lastK.get(lastK.size() - 1);
        Map<Long, Long> achievementCountByRepository = achievementRepository.countPerRepository(user).stream()
                .collect(Collectors.toMap(RepositoryCountProjection::getRepositoryId, RepositoryCountProjection::getTotal));

        Optional<GithubRepository> silentRepo = githubRepositoryRepository.findByUser(user).stream()
                .filter(r -> !dominantRepo.equals(r.getName()))
                .filter(r -> achievementCountByRepository.getOrDefault(r.getId(), 0L) == 0L)
                .filter(r -> githubCommitRepository.existsByRepositoryAndCommittedAtAfter(
                        r, oldestOfK.getGeneratedAt()))
                .max(Comparator.comparingLong(r -> githubCommitRepository.countByRepositoryAndCommittedAtAfter(
                        r, oldestOfK.getGeneratedAt())));

        if (silentRepo.isEmpty()) {
            return List.of();
        }

        GithubRepository other = silentRepo.get();
        long otherCommits = githubCommitRepository.countByRepositoryAndCommittedAtAfter(other, oldestOfK.getGeneratedAt());

        String statement = "Your last %d achievements are all from %s. Nothing yet from %s, which has %d commit%s in the same period."
                .formatted(window, dominantRepo, other.getFullName(), otherCommits, otherCommits == 1 ? "" : "s");

        List<String> evidence = List.of(
                "Last %d achievements: %s".formatted(window, lastK.stream().map(AchievementEntity::getTitle).toList()),
                "%s: %d commits since %s, zero achievements".formatted(
                        other.getFullName(), otherCommits, oldestOfK.getGeneratedAt().toLocalDate()));

        return List.of(new Observation(ObservationType.CONCENTRATION, statement, evidence,
                "Consider running analysis on " + other.getFullName() + "."));
    }
}
