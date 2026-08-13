package com.careeros.backend.schedule;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The FOR UPDATE SKIP LOCKED behaviour in {@link ScheduledPostRepository#claimDuePosts}
 * only exists to make two concurrent workers safe against real Postgres row
 * locking — an H2 in-memory swap or a mocked repository can't exercise that,
 * so this runs against a real Postgres via Testcontainers (nothing suitable
 * already existed in the project).
 *
 * {@code @Transactional(NOT_SUPPORTED)} turns off {@code @DataJpaTest}'s usual
 * per-test rollback wrapper: each worker thread below needs its own real,
 * independently committed transaction/connection, not a shared one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ScheduledPostRepositoryClaimLoopTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ScheduledPostRepository scheduledPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .githubId(System.nanoTime())
                .username("claim-loop-user")
                .encryptedGithubAccessToken("token")
                .build());
    }

    @AfterEach
    void tearDown() {
        scheduledPostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void twoWorkersClaimingConcurrentlyNeverClaimTheSameRow() throws Exception {
        List<Long> postIds = createDuePosts(50);

        List<Set<Long>> claims = claimConcurrentlyUntilExhausted();

        assertDisjointAndCovers(claims.get(0), claims.get(1), postIds);
    }

    @Test
    void releaseStuckDoesNotHandOutAPostToTwoWorkersAtOnce() throws Exception {
        List<Long> postIds = createDuePosts(30);

        // Simulate a worker that claimed everything, then crashed before
        // marking any post POSTED — it never releases its claim itself.
        inTransaction(() -> scheduledPostRepository.claimDuePosts("dead-worker", postIds.size()));

        // A cutoff in the future always catches "claimed_at < cutoff", so this
        // sweeps every row back to SCHEDULED without needing to backdate rows.
        int released = inTransaction(() ->
                scheduledPostRepository.releaseStuck(OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now()));
        assertThat(released).isEqualTo(postIds.size());

        List<Set<Long>> claims = claimConcurrentlyUntilExhausted();

        assertDisjointAndCovers(claims.get(0), claims.get(1), postIds);
    }

    /** Runs two workers racing to drain the queue, each polling in small batches. */
    private List<Set<Long>> claimConcurrentlyUntilExhausted() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<List<Long>> worker = () -> {
            List<Long> claimed = new ArrayList<>();
            ready.countDown();
            go.await();
            List<Long> batch;
            do {
                batch = inTransaction(() -> scheduledPostRepository.claimDuePosts(
                        Thread.currentThread().getName(), 5));
                claimed.addAll(batch);
            } while (!batch.isEmpty());
            return claimed;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> worker1 = pool.submit(worker);
            Future<List<Long>> worker2 = pool.submit(worker);
            ready.await();
            go.countDown();

            List<Long> claimedByWorker1 = worker1.get(30, TimeUnit.SECONDS);
            List<Long> claimedByWorker2 = worker2.get(30, TimeUnit.SECONDS);
            return List.of(new HashSet<>(claimedByWorker1), new HashSet<>(claimedByWorker2));
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertDisjointAndCovers(Set<Long> claimedByWorker1, Set<Long> claimedByWorker2,
                                          List<Long> expectedPostIds) {
        assertThat(claimedByWorker1).as("worker 1 never claims the same row twice")
                .doesNotContainAnyElementsOf(claimedByWorker2);

        Set<Long> allClaimed = new HashSet<>(claimedByWorker1);
        allClaimed.addAll(claimedByWorker2);
        assertThat(allClaimed).as("every due post is claimed exactly once, across both workers")
                .containsExactlyInAnyOrderElementsOf(expectedPostIds);
    }

    private List<Long> createDuePosts(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ScheduledPost post = scheduledPostRepository.save(ScheduledPost.builder()
                    .user(user)
                    .platform(PostPlatform.LINKEDIN)
                    .body("post " + i)
                    .status(PostStatus.SCHEDULED)
                    .scheduledFor(OffsetDateTime.now().minusMinutes(1))
                    .build());
            ids.add(post.getId());
        }
        return ids;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
