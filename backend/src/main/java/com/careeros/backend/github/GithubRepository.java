package com.careeros.backend.github;

import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "github_repositories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_repo_user_github_id",
                columnNames = {"user_id", "github_repository_id"}
        )
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_repository_id", nullable = false)
    private Long githubRepositoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String language;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "private", nullable = false)
    private Boolean privateRepo;

    @Column(name = "html_url", columnDefinition = "TEXT")
    private String htmlUrl;

    @Column(name = "created_at_github")
    private LocalDateTime createdAtGithub;

    @Column(name = "updated_at_github")
    private LocalDateTime updatedAtGithub;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    /** Null until the repository has been analysed at least once. */
    @Column(name = "last_analyzed_at")
    private OffsetDateTime lastAnalyzedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_outcome", length = 32)
    private AnalysisOutcome analysisOutcome;

    /** Written for a UI to show verbatim, so it is a sentence, not a code. */
    @Column(name = "analysis_reason", columnDefinition = "TEXT")
    private String analysisReason;
}