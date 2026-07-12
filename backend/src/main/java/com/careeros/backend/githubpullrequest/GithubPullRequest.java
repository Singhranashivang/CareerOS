package com.careeros.backend.githubpullrequest;

import com.careeros.backend.github.GithubRepository;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_pull_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubPullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GithubRepository repository;


    @Column(name = "github_pull_request_id", nullable = false, unique = true)
    private Long githubPullRequestId;


    private String title;


    @Column(columnDefinition = "TEXT")
    private String body;


    private String state;


    @Column(name = "html_url")
    private String htmlUrl;


    @Column(name = "author_login")
    private String authorLogin;


    private Boolean merged;


    @Column(name = "created_at_github")
    private LocalDateTime createdAtGithub;


    @Column(name = "updated_at_github")
    private LocalDateTime updatedAtGithub;


    @Column(name = "merged_at_github")
    private LocalDateTime mergedAtGithub;


    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}