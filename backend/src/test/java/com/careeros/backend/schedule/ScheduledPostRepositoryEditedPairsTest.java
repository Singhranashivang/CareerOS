package com.careeros.backend.schedule;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findEditedPairs is the single definition of "the user rewrote this", and it
 * leans on things a mock can't check: the V27 column existing, btrim actually
 * stripping newlines and not just spaces, and the platform/owner/null filters.
 * The whitespace case here is why the query is native — plain SQL TRIM strips
 * spaces only, so a textarea's trailing newline read as a rewrite.
 *
 * Runs against real Postgres via Testcontainers, same as
 * ScheduledPostRepositoryClaimLoopTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ScheduledPostRepositoryEditedPairsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ScheduledPostRepository scheduledPostRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .githubId(System.nanoTime())
                .username("edited-pairs-user")
                .githubAccessToken("token")
                .build());
    }

    @Test
    void findsOnlyThePostsWhoseBodyDivergedFromTheGeneratedText() {
        save(PostPlatform.LINKEDIN, "I rewrote this one myself.", "The model wrote this.");
        save(PostPlatform.LINKEDIN, "Untouched.", "Untouched.");
        save(PostPlatform.LINKEDIN, "Written from scratch.", null);

        List<ScheduledPost> pairs = scheduledPostRepository.findEditedPairs(user, 10);

        assertThat(pairs).extracting(ScheduledPost::getBody)
                .containsExactly("I rewrote this one myself.");
    }

    @Test
    void whitespaceOnlyDifferencesAreNotEdits() {
        save(PostPlatform.LINKEDIN, "  Same text.\n", "Same text.");

        assertThat(scheduledPostRepository.findEditedPairs(user, 10)).isEmpty();
    }

    @Test
    void otherPlatformsAreNotLinkedInVoice() {
        save(PostPlatform.X, "A rewritten tweet.", "The generated tweet.");

        assertThat(scheduledPostRepository.findEditedPairs(user, 10)).isEmpty();
    }

    @Test
    void newestEditFirstAndTheLimitCaps() {
        save(PostPlatform.LINKEDIN, "oldest edit", "generated");
        save(PostPlatform.LINKEDIN, "middle edit", "generated");
        save(PostPlatform.LINKEDIN, "newest edit", "generated");

        List<ScheduledPost> pairs = scheduledPostRepository.findEditedPairs(user, 2);

        assertThat(pairs).extracting(ScheduledPost::getBody)
                .containsExactly("newest edit", "middle edit");
    }

    @Test
    void anotherUsersRewritesAreNeverBorrowed() {
        User other = userRepository.save(User.builder()
                .githubId(System.nanoTime()).username("other").githubAccessToken("t").build());
        scheduledPostRepository.save(ScheduledPost.builder()
                .user(other).platform(PostPlatform.LINKEDIN)
                .body("Their rewrite.").generatedBody("generated")
                .status(PostStatus.DRAFT).userTimezone("UTC")
                .build());

        assertThat(scheduledPostRepository.findEditedPairs(user, 10)).isEmpty();
    }

    /**
     * createdAt is @PrePersist-set and updatable=false, so insertion order is
     * the only order these get — which is exactly what the query's id tiebreak
     * makes deterministic. Saved oldest-first; "newest" is the last one saved.
     */
    private void save(PostPlatform platform, String body, String generatedBody) {
        scheduledPostRepository.saveAndFlush(ScheduledPost.builder()
                .user(user)
                .platform(platform)
                .body(body)
                .generatedBody(generatedBody)
                .status(PostStatus.DRAFT)
                .userTimezone("UTC")
                .build());
    }
}
