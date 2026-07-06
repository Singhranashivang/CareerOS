package com.careeros.backend.github;

import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_repositories")
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

    @Column(name = "github_repository_id", nullable = false, unique = true)
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
}