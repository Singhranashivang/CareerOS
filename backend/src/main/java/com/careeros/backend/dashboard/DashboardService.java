package com.careeros.backend.dashboard;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendationService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementEntity;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementPersistenceService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RepositoryRecommendationService recommendationService;
    private final WeeklyAchievementPersistenceService weeklyService;
    private final AchievementPersistenceService achievementService;
    private final LinkedInPostPersistenceService linkedInService;

    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final GithubPullRequestRepository pullRequestRepository;

    public DashboardResponse getDashboard(User user) {

        List<GithubRepository> repositories =
                repositoryRepository.findByUser(user);

        int commitCount = repositories.stream()
                .mapToInt(r -> commitRepository.findByRepository(r).size())
                .sum();

        int pullRequestCount = repositories.stream()
                .mapToInt(r -> pullRequestRepository.findByRepository(r).size())
                .sum();

        RepositoryRecommendation recommendation =
                recommendationService.recommend(user)
                        .stream()
                        .findFirst()
                        .orElse(null);

        WeeklyAchievementEntity weeklySummary =
                weeklyService.findLatest(user)
                        .orElse(null);

        LinkedInPostEntity linkedInPost =
                linkedInService.findByUser(user)
                        .orElse(null);

        List<AchievementEntity> achievements =
                achievementService.findByUser(user);

        System.out.println("==================================");
        System.out.println("Achievement count = " + achievements.size());

        for (AchievementEntity achievement : achievements) {
            System.out.println(
                    "Achievement: " +
                            achievement.getTitle() +
                            " | User ID = " +
                            achievement.getUser().getId()
            );
        }

        System.out.println("==================================");

        DashboardStats stats = DashboardStats.builder()
                .repositories(repositories.size())
                .commits(commitCount)
                .pullRequests(pullRequestCount)
                .achievements(achievements.size())
                .build();

        return DashboardResponse.builder()
                .recommendedRepository(recommendation)
                .weeklySummary(weeklySummary)
                .achievements(achievements)
                .linkedInPost(linkedInPost)
                .stats(stats)
                .build();
    }
}