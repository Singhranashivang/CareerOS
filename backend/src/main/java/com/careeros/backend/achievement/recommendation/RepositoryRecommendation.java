package com.careeros.backend.achievement.recommendation;

import com.careeros.backend.github.GithubRepository;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RepositoryRecommendation {

    private GithubRepository repository;

    private int score;

    private List<String> reasons;
}