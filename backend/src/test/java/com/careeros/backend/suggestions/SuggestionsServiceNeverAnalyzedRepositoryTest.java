package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * hasUnanalyzedOwnerCommits's never-analysed branch used to query
 * existsByRepositoryAndCommittedAtAfter with LocalDateTime.MIN as a sentinel
 * "since" — a year outside Postgres's representable timestamp range, so the
 * driver overflowed it into a garbage date Postgres then rejected. A mocked
 * GithubCommitRepository accepts LocalDateTime.MIN without complaint, so
 * only a real Postgres catches this; hence Testcontainers here rather than
 * the mocked SuggestionsServiceTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SuggestionsServiceNeverAnalyzedRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AchievementRepository achievementRepository;
    @Autowired
    private GithubRepositoryRepository githubRepositoryRepository;
    @Autowired
    private GithubCommitRepository githubCommitRepository;
    @Autowired
    private ScheduledPostRepository scheduledPostRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void neverAnalyzedRepositoryWithAnOwnerCommitIsSurfacedWithoutThrowing() {
        User user = userRepository.save(User.builder()
                .githubId(System.nanoTime())
                .username("never-analyzed-user")
                .githubAccessToken("token")
                .build());
        GithubRepository repository = githubRepositoryRepository.save(GithubRepository.builder()
                .user(user)
                .githubRepositoryId(System.nanoTime())
                .name("repo")
                .fullName("u/repo")
                .privateRepo(false)
                .lastAnalyzedAt(null)
                .build());
        githubCommitRepository.save(GithubCommit.builder()
                .repository(repository)
                .githubCommitSha("sha-" + System.nanoTime())
                .message("initial commit")
                .committedAt(LocalDateTime.now())
                .build());

        SuggestionsService service = new SuggestionsService(achievementRepository, githubRepositoryRepository,
                githubCommitRepository, scheduledPostRepository, mock(SuggestionScorer.class), new ObjectMapper());

        SuggestionsResponse result = service.suggestionsFor(user);

        assertThat(result.repositoriesWithNoAchievements()).containsExactly("u/repo");
    }
}
