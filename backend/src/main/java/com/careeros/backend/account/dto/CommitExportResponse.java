package com.careeros.backend.account.dto;

import com.careeros.backend.githubcommit.GithubCommit;

import java.time.LocalDateTime;

/**
 * GithubCommit's own repository field is a lazy @ManyToOne with no
 * @JsonIgnore — serializing the entity directly outside a transaction throws
 * LazyInitializationException, the exact bug this whole app's controller
 * layer was already audited for. DTO, not the entity.
 */
public record CommitExportResponse(
        Long id,
        Long repositoryId,
        String repositoryName,
        String githubCommitSha,
        String message,
        String authorName,
        String authorEmail,
        Long authorGithubId,
        String authorGithubLogin,
        LocalDateTime committedAt,
        String htmlUrl
) {
    public static CommitExportResponse from(GithubCommit c) {
        return new CommitExportResponse(
                c.getId(),
                c.getRepository().getId(),
                c.getRepository().getName(),
                c.getGithubCommitSha(),
                c.getMessage(),
                c.getAuthorName(),
                c.getAuthorEmail(),
                c.getAuthorGithubId(),
                c.getAuthorGithubLogin(),
                c.getCommittedAt(),
                c.getHtmlUrl()
        );
    }
}
