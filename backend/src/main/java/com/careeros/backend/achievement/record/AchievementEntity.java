package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.AchievementType;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /** Null for sources that aren't a repository (Jira, Notion, cross-repo). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @JsonIgnore
    private GithubRepository repository;

    /** Denormalised label; survives repository deletion. */
    @Column(name = "repository_name")
    private String repositoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AchievementSource source = AchievementSource.GITHUB;

    @Enumerated(EnumType.STRING)
    private AchievementType type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String resumeBullet;

    @Column(columnDefinition = "TEXT")
    private String starSituation;

    @Column(columnDefinition = "TEXT")
    private String starTask;

    @Column(columnDefinition = "TEXT")
    private String starAction;

    @Column(columnDefinition = "TEXT")
    private String starResult;

    @Column(columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(columnDefinition = "TEXT")
    private String technologiesJson;

    /** The cluster's commit SHAs (sorted JSON array) — cited SHAs are the cluster's, not the whole repository's. */
    @Column(name = "cited_commit_shas_json", columnDefinition = "TEXT")
    private String citedCommitShasJson;

    private double confidence;

    private LocalDateTime generatedAt;
}