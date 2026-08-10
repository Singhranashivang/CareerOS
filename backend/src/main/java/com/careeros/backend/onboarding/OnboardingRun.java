package com.careeros.backend.onboarding;

import com.careeros.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "onboarding_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OnboardingStatus status = OnboardingStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OnboardingStage stage = OnboardingStage.CONNECTING;

    @Column(name = "repos_found", nullable = false)
    @Builder.Default
    private int reposFound = 0;

    @Column(name = "commits_synced", nullable = false)
    @Builder.Default
    private int commitsSynced = 0;

    /** Repos processed in the PR-sync loop, not a PR count — syncPullRequests returns void. */
    @Column(name = "pr_repos_synced", nullable = false)
    @Builder.Default
    private int prReposSynced = 0;

    @Column(name = "repos_to_analyze", nullable = false)
    @Builder.Default
    private int reposToAnalyze = 0;

    @Column(name = "repos_analyzed", nullable = false)
    @Builder.Default
    private int reposAnalyzed = 0;

    @Column(name = "achievements_created", nullable = false)
    @Builder.Default
    private int achievementsCreated = 0;

    @Column(name = "current_repository_name")
    private String currentRepositoryName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.startedAt == null) {
            this.startedAt = this.createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
