package com.careeros.backend.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stands in until LinkedIn approves the w_member_social scope. Logs the body and
 * reports success, which makes the whole claim → publish → POSTED pipeline
 * exercisable today. LinkedInPublisher is a new class next to this one; nothing
 * else in the package changes when it lands.
 */
@Component
@Slf4j
public class NoOpPublisher implements PlatformPublisher {

    @Override
    public PostPlatform platform() {
        return PostPlatform.LINKEDIN;
    }

    @Override
    public PublishResult publish(ScheduledPost post) {
        log.info("NoOpPublisher would post {} chars to {}:\n{}",
                post.getBody().length(), post.getPlatform(), post.getBody());
        return new PublishResult("noop-" + UUID.randomUUID());
    }
}
