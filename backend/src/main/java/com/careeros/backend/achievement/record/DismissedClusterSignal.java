package com.careeros.backend.achievement.record;

import com.careeros.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * What a dismissed achievement's cluster touched — recorded so dismissing
 * teaches the generator something, rather than just hiding one row. See
 * AchievementPersistenceService.dismiss (where this is written) and
 * DismissedAreaOverlapGate (where it's read, before generating a new
 * cluster for the same repository).
 */
@Entity
@Table(name = "dismissed_cluster_signals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DismissedClusterSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    /** Same shape as AchievementEntity.technologiesJson — copied from the dismissed row. */
    @Column(name = "technologies_json", columnDefinition = "TEXT")
    private String technologiesJson;

    /** Same shape as AchievementEntity.filePathsJson — copied from the dismissed row. */
    @Column(name = "file_paths_json", columnDefinition = "TEXT")
    private String filePathsJson;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt;
}
