package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionsService {

    private final AchievementRepository achievementRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final SuggestionScorer suggestionScorer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SuggestionsResponse suggestionsFor(User user) {

        List<AchievementEntity> nonDismissed =
                achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user);

        // NoOpPublisher (still standing in for the real LinkedIn publish call)
        // marks every post POSTED and stamps postedAt regardless of whether
        // anything actually went anywhere — it just logs the body and returns
        // "noop-<uuid>" as the external id. Nothing here should treat that as
        // "the user posted"; filtered out before anything else reads `posted`.
        List<ScheduledPost> posted = scheduledPostRepository.findByUserAndStatus(user, PostStatus.POSTED).stream()
                .filter(SuggestionsService::wasActuallyPublished)
                .toList();
        List<AchievementEntity> postedAchievements = posted.stream()
                .map(ScheduledPost::getAchievement)
                .filter(Objects::nonNull)
                .toList();

        Set<Long> postedAchievementIds = postedAchievements.stream()
                .map(AchievementEntity::getId)
                .collect(Collectors.toSet());
        Set<String> repositoriesWithPostedWork = postedAchievements.stream()
                .map(AchievementEntity::getRepositoryName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SuggestedAchievementResponse> ranked = nonDismissed.stream()
                .filter(a -> !postedAchievementIds.contains(a.getId()))
                .map(a -> toRanked(a, repositoriesWithPostedWork.contains(a.getRepositoryName())))
                .sorted(Comparator.comparingDouble(SuggestedAchievementResponse::score).reversed())
                .toList();

        OffsetDateTime lastPostedAt = posted.stream()
                .map(ScheduledPost::getPostedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Long daysSinceLastPost = lastPostedAt == null
                ? null
                : Duration.between(lastPostedAt, OffsetDateTime.now()).toDays();

        Set<String> allTechnologies = technologiesFrom(nonDismissed);
        Set<String> postedTechnologies = technologiesFrom(postedAchievements);
        allTechnologies.removeAll(postedTechnologies);

        return new SuggestionsResponse(
                ranked,
                daysSinceLastPost,
                lastPostedAt,
                List.copyOf(allTechnologies),
                repositoriesWithNoAchievements(user));
    }

    private SuggestedAchievementResponse toRanked(AchievementEntity achievement, boolean repositoryHasPostedWork) {
        var scored = suggestionScorer.score(achievement, repositoryHasPostedWork);
        return new SuggestedAchievementResponse(
                achievement.getId(),
                achievement.getTitle(),
                achievement.getRepositoryName(),
                achievement.getConfidence(),
                achievement.getGeneratedAt(),
                scored.score(),
                scored.reason());
    }

    /** Sorted (TreeSet), so the gap list and the "posted" set it's diffed against compare predictably in tests/logs. */
    private Set<String> technologiesFrom(List<AchievementEntity> achievements) {
        Set<String> technologies = new TreeSet<>();
        for (AchievementEntity achievement : achievements) {
            String json = achievement.getTechnologiesJson();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                technologies.addAll(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("Failed to parse technologiesJson for achievement {}, skipping it", achievement.getId(), e);
            }
        }
        return technologies;
    }

    /**
     * Zero achievements alone isn't actionable — most repos with zero are
     * repos already analysed that genuinely had nothing to claim (a fork,
     * config-only changes, evidence below the floor), and re-listing those
     * every time is just noise. Only surface a repo if there's owner-authored
     * work newer than its last analysis — i.e. there's something the
     * generator hasn't looked at yet, not just a repo it already declined.
     */
    private List<String> repositoriesWithNoAchievements(User user) {

        Map<Long, Long> achievementCountByRepository = achievementRepository.countPerRepository(user).stream()
                .collect(Collectors.toMap(RepositoryCountProjection::getRepositoryId, RepositoryCountProjection::getTotal));

        return githubRepositoryRepository.findByUser(user).stream()
                .filter(repository -> achievementCountByRepository.getOrDefault(repository.getId(), 0L) == 0L)
                .filter(this::hasUnanalyzedOwnerCommits)
                .map(GithubRepository::getFullName)
                .sorted()
                .toList();
    }

    /**
     * Never-analysed repos pass automatically if they have any commit at
     * all. Used to express "any commit" as existsByRepositoryAndCommittedAtAfter
     * with LocalDateTime.MIN as the sentinel "since" — that value is year
     * -999999999, far outside Postgres's timestamp range (4713 BC-294276 AD),
     * and the driver doesn't reject it cleanly: it silently overflows into a
     * garbage date ("timestamp out of range: 169087565-03-15...") that
     * Postgres then rejects. countByRepository sidesteps the sentinel
     * entirely instead of finding a safe substitute for it.
     */
    private boolean hasUnanalyzedOwnerCommits(GithubRepository repository) {
        if (repository.getLastAnalyzedAt() == null) {
            return githubCommitRepository.countByRepository(repository) > 0;
        }
        return githubCommitRepository.existsByRepositoryAndCommittedAtAfter(
                repository, repository.getLastAnalyzedAt().toLocalDateTime());
    }

    /** NoOpPublisher (see suggestionsFor's comment) always prefixes its fake external id this way. */
    private static boolean wasActuallyPublished(ScheduledPost post) {
        return post.getExternalPostId() == null || !post.getExternalPostId().startsWith("noop-");
    }
}
