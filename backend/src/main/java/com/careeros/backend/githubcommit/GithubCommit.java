package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GithubRepository repository;

    @Column(name = "github_commit_sha", nullable = false, unique = true)
    private String githubCommitSha;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "committed_at", nullable = false)
    private LocalDateTime committedAt;

    @Column(name = "html_url", columnDefinition = "TEXT")
    private String htmlUrl;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}