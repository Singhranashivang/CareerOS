package com.careeros.backend.achievement.weekly;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubpullrequest.GithubPullRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WeeklyAchievementInput {

    private List<GithubRepository> repositories;

    private List<GithubCommit> commits;

    private List<GithubPullRequest> pullRequests;

}