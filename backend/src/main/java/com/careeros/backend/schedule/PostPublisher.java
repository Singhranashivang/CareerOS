package com.careeros.backend.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
@Slf4j
public class PostPublisher {

    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 10;
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);

    private final ScheduledPostService scheduledPostService;
    private final Map<PostPlatform, PlatformPublisher> publishers;
    private final String workerId;

    public PostPublisher(ScheduledPostService scheduledPostService,
                         List<PlatformPublisher> publishers,
                         @Value("${app.scheduling.worker-id}") String workerId) {
        this.scheduledPostService = scheduledPostService;
        this.publishers = publishers.stream()
                .collect(Collectors.toMap(PlatformPublisher::platform, Function.identity()));
        this.workerId = workerId;
        log.info("Post publisher {} started with platforms {}", workerId, this.publishers.keySet());
    }

    @Scheduled(fixedDelay = 60_000)
    public void publishDue() {

        int released = scheduledPostService.releaseStuck(
                OffsetDateTime.now().minus(STUCK_AFTER));
        if (released > 0) {
            log.warn("Released {} posts stuck in PUBLISHING", released);
        }

        List<Long> claimed = scheduledPostService.claimDue(workerId, BATCH_SIZE);
        if (claimed.isEmpty()) {
            return;
        }

        log.info("Worker {} claimed {} due posts", workerId, claimed.size());
        claimed.forEach(this::publishOne);
    }

    /**
     * Every path out of here writes a terminal status, so no row is left in
     * PUBLISHING. If even markFailed throws, the stuck sweep is the backstop.
     */
    private void publishOne(Long id) {
        try {
            ScheduledPost post = scheduledPostService.loadForPublish(id);

            PlatformPublisher publisher = publishers.get(post.getPlatform());
            if (publisher == null) {
                throw new IllegalStateException("No publisher for " + post.getPlatform());
            }

            PublishResult result = publisher.publish(post);
            scheduledPostService.markPosted(id, result.externalPostId());
            log.info("Published post {} as {}", id, result.externalPostId());

        } catch (Exception e) {
            log.warn("Publishing post {} failed: {}", id, e.toString());
            try {
                scheduledPostService.markFailed(id, e.toString(), MAX_ATTEMPTS);
            } catch (Exception fatal) {
                log.error("Could not record failure for post {}; sweeper will reclaim it",
                        id, fatal);
            }
        }
    }
}
