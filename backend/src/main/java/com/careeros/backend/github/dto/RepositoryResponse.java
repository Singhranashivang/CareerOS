package com.careeros.backend.github.dto;

import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record RepositoryResponse(
        Long id,
        /** Null means never analysed — distinct from analysed-and-declined. */
        AnalysisOutcome analysisOutcome,
        String analysisReason,
        OffsetDateTime lastAnalyzedAt,
        int achievementCount,
        int commitCount,
        Long githubRepositoryId,
        String name,
        String fullName,
        String description,
        String language,
        String defaultBranch,
        boolean isPrivate,
        String htmlUrl,
        LocalDateTime createdAtGithub,
        LocalDateTime updatedAtGithub,
        LocalDateTime syncedAt
) {
    public static RepositoryResponse from(
            GithubRepository r,
            int achievementCount,
            int commitCount
    ) {
        return new RepositoryResponse(
                r.getId(),
                r.getAnalysisOutcome(),
                r.getAnalysisReason(),
                r.getLastAnalyzedAt(),
                achievementCount,
                commitCount,
                r.getGithubRepositoryId(),
                r.getName(),
                r.getFullName(),
                r.getDescription(),
                r.getLanguage(),
                r.getDefaultBranch(),
                Boolean.TRUE.equals(r.getPrivateRepo()),
                r.getHtmlUrl(),
                r.getCreatedAtGithub(),
                r.getUpdatedAtGithub(),
                r.getSyncedAt()
        );
    }
}