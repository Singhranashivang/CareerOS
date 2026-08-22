package com.careeros.backend.achievement.recommendation;

import com.careeros.backend.github.GithubRepository;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RepositoryRecommendation {

    private GithubRepository repository;

    private int commitCount;

    private int score;

    private List<String> reasons;

    /** Boundary conversion — never serialize this type (or the entity it holds) directly. */
    public RecommendedRepositoryDto toDto() {
        return RecommendedRepositoryDto.builder()
                .id(repository.getId())
                .name(repository.getName())
                .commitCount(commitCount)
                .score(score)
                .build();
    }
}