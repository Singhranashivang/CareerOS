package com.careeros.backend.schedule;

/**
 * One implementation per platform. Failure is signalled by throwing — PostPublisher
 * turns that into a retry with backoff.
 */
public interface PlatformPublisher {

    PostPlatform platform();

    PublishResult publish(ScheduledPost post);
}
