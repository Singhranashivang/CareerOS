package com.careeros.backend.schedule;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scheduled_posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JsonIgnore: the User relation carries the GitHub access token. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /** Null when the post was written from scratch rather than an achievement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id")
    @JsonIgnore
    private AchievementEntity achievement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PostPlatform platform;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    /** The instant, in UTC. userTimezone renders it back as the user's clock. */
    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "user_timezone", nullable = false, length = 64)
    @Builder.Default
    private String userTimezone = "UTC";

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "external_post_id")
    private String externalPostId;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Publish succeeded. Releases the claim so no row lingers in PUBLISHING. */
    void recordSuccess(String externalId) {
        this.status = PostStatus.POSTED;
        this.externalPostId = externalId;
        this.postedAt = OffsetDateTime.now();
        this.failureReason = null;
        releaseClaim();
    }

    /**
     * Publish failed. Back to SCHEDULED with attempt^2 minutes of backoff, or
     * FAILED once maxAttempts is spent. Kept on the entity so the transition is
     * unit-testable without a database.
     */
    void recordFailure(String reason, int maxAttempts) {
        this.attemptCount++;
        this.failureReason = reason;
        if (this.attemptCount >= maxAttempts) {
            this.status = PostStatus.FAILED;
        } else {
            this.status = PostStatus.SCHEDULED;
            this.scheduledFor = OffsetDateTime.now()
                    .plusMinutes((long) this.attemptCount * this.attemptCount);
        }
        releaseClaim();
    }

    private void releaseClaim() {
        this.claimedBy = null;
        this.claimedAt = null;
    }
}
