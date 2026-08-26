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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionsServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final ScheduledPostRepository scheduledPostRepository = mock(ScheduledPostRepository.class);
    private final SuggestionScorer suggestionScorer = mock(SuggestionScorer.class);

    private final SuggestionsService service = new SuggestionsService(
            achievementRepository, githubRepositoryRepository, githubCommitRepository, scheduledPostRepository,
            suggestionScorer, new ObjectMapper());

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void aNoOpPublishedPostDoesNotCountAsPostedAnywhere() {
        // NoOpPublisher marks status=POSTED and stamps postedAt without
        // sending anything — its externalPostId always starts "noop-".
        AchievementEntity achievement = AchievementEntity.builder().id(1L).title("A")
                .repositoryName("repo").technologiesJson("[\"Java\"]").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(achievement));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(achievement).postedAt(OffsetDateTime.now())
                        .externalPostId("noop-" + java.util.UUID.randomUUID()).build()));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(suggestionScorer.score(any(), anyBoolean(), any()))
                .thenReturn(new SuggestionScorer.Scored(1.0, "reason"));

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.suggestions()).extracting(SuggestedAchievementResponse::achievementId).containsExactly(1L);
        assertThat(result.daysSinceLastPost()).isNull();
        assertThat(result.lastPostedAt()).isNull();
        assertThat(result.technologiesNotYetPosted()).containsExactly("Java");
    }

    @Test
    void excludesAchievementsWithAPostedScheduledPost() {
        AchievementEntity posted = AchievementEntity.builder().id(1L).title("Posted").build();
        AchievementEntity unposted = AchievementEntity.builder().id(2L).title("Unposted").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(posted, unposted));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(posted).postedAt(OffsetDateTime.now()).build()));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(suggestionScorer.score(any(), anyBoolean(), any()))
                .thenReturn(new SuggestionScorer.Scored(1.0, "reason"));

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).achievementId()).isEqualTo(2L);
    }

    @Test
    void suggestionsAreSortedByScoreDescending() {
        AchievementEntity low = AchievementEntity.builder().id(1L).title("Low").build();
        AchievementEntity high = AchievementEntity.builder().id(2L).title("High").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(low, high));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(suggestionScorer.score(eq(low), eq(false), any())).thenReturn(new SuggestionScorer.Scored(0.2, "low reason"));
        when(suggestionScorer.score(eq(high), eq(false), any())).thenReturn(new SuggestionScorer.Scored(0.9, "high reason"));

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.suggestions()).extracting(SuggestedAchievementResponse::achievementId)
                .containsExactly(2L, 1L);
    }

    @Test
    void passesWhetherTheRepositoryHasPostedWorkToTheScorer() {
        AchievementEntity postedInRepoA = AchievementEntity.builder().id(1L).repositoryName("repoA").build();
        AchievementEntity unpostedInRepoA = AchievementEntity.builder().id(2L).repositoryName("repoA").build();
        AchievementEntity unpostedInRepoB = AchievementEntity.builder().id(3L).repositoryName("repoB").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(postedInRepoA, unpostedInRepoA, unpostedInRepoB));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(postedInRepoA).postedAt(OffsetDateTime.now()).build()));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(suggestionScorer.score(any(), anyBoolean(), any()))
                .thenReturn(new SuggestionScorer.Scored(1.0, "reason"));

        service.suggestionsFor(USER);

        verify(suggestionScorer).score(unpostedInRepoA, true, null);
        verify(suggestionScorer).score(unpostedInRepoB, false, null);
    }

    @Test
    void daysSinceLastPostUsesTheMostRecentPostedAt() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(List.of());
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().postedAt(OffsetDateTime.now().minusDays(10)).build(),
                ScheduledPost.builder().postedAt(OffsetDateTime.now().minusDays(3)).build()));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.daysSinceLastPost()).isEqualTo(3L);
        assertThat(result.lastPostedAt()).isNotNull();
    }

    @Test
    void lastPostedAtAndDaysSinceAreNullWhenNeverPosted() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(List.of());
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.daysSinceLastPost()).isNull();
        assertThat(result.lastPostedAt()).isNull();
    }

    @Test
    void technologiesNotYetPostedIsTheGapBetweenAllAndPostedAchievements() {
        AchievementEntity postedAchievement = AchievementEntity.builder().id(1L)
                .technologiesJson("[\"Java\",\"Postgres\"]").build();
        AchievementEntity unpostedAchievement = AchievementEntity.builder().id(2L)
                .technologiesJson("[\"Java\",\"React\"]").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(postedAchievement, unpostedAchievement));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(postedAchievement).postedAt(OffsetDateTime.now()).build()));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(suggestionScorer.score(any(), anyBoolean(), any()))
                .thenReturn(new SuggestionScorer.Scored(1.0, "reason"));

        SuggestionsResponse result = service.suggestionsFor(USER);

        // Java and Postgres appear in posted work; only React is a real gap.
        assertThat(result.technologiesNotYetPosted()).containsExactly("React");
    }

    @Test
    void repositoriesWithNoAchievementsListsReposMissingFromTheAchievementCount() {
        GithubRepository withAchievements = GithubRepository.builder().id(1L).fullName("u/has-achievements").build();
        GithubRepository withoutAchievements = GithubRepository.builder().id(2L).fullName("u/empty").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(List.of());
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        when(githubRepositoryRepository.findByUser(USER))
                .thenReturn(List.of(withAchievements, withoutAchievements));
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of(
                projection(1L, 3L)));
        // withoutAchievements has no lastAnalyzedAt — hasUnanalyzedOwnerCommits takes the
        // never-analyzed branch (countByRepository), not existsByRepositoryAndCommittedAtAfter.
        when(githubCommitRepository.countByRepository(withoutAchievements)).thenReturn(1L);

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.repositoriesWithNoAchievements()).containsExactly("u/empty");
    }

    @Test
    void repositoriesWithNoAchievementsExcludesOnesWithNoUnanalyzedOwnerCommits() {
        // Zero achievements alone isn't actionable — already analysed and
        // genuinely had nothing to claim, with no new owner commits since.
        GithubRepository alreadyFullyAnalyzed = GithubRepository.builder().id(2L).fullName("u/nothing-new").build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(List.of());
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(alreadyFullyAnalyzed));
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(githubCommitRepository.existsByRepositoryAndCommittedAtAfter(eq(alreadyFullyAnalyzed), any()))
                .thenReturn(false);

        SuggestionsResponse result = service.suggestionsFor(USER);

        assertThat(result.repositoriesWithNoAchievements()).isEmpty();
    }

    private static RepositoryCountProjection projection(Long repositoryId, long total) {
        return new RepositoryCountProjection() {
            public Long getRepositoryId() {
                return repositoryId;
            }

            public long getTotal() {
                return total;
            }
        };
    }

}
