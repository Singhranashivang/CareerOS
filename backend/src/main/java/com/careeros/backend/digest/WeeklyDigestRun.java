package com.careeros.backend.digest;

import com.careeros.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row per user, upserted every run — see the migration for why this isn't a log table. */
@Entity
@Table(name = "weekly_digest_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyDigestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WeeklyDigestOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "repos_synced", nullable = false)
    @Builder.Default
    private int reposSynced = 0;

    @Column(name = "commits_synced", nullable = false)
    @Builder.Default
    private int commitsSynced = 0;

    @Column(name = "repos_analyzed", nullable = false)
    @Builder.Default
    private int reposAnalyzed = 0;

    @Column(name = "achievements_created", nullable = false)
    @Builder.Default
    private int achievementsCreated = 0;
}
