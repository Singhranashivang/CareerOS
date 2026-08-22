package com.careeros.backend.dashboard;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostResponse;
import com.careeros.backend.achievement.recommendation.RecommendedRepositoryDto;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendationService;
import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.achievement.timeline.AchievementTimelineService;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementPersistenceService;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementResponse;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RepositoryRecommendationService recommendationService;
    private final WeeklyAchievementPersistenceService weeklyService;
    private final AchievementTimelineService achievementTimelineService;
    private final LinkedInPostPersistenceService linkedInService;

    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final GithubPullRequestRepository pullRequestRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(User user) {

        List<GithubRepository> repositories =
                repositoryRepository.findByUser(user);

        int commitCount = repositories.stream()
                .mapToInt(r -> commitRepository.findByRepository(r).size())
                .sum();

        int pullRequestCount = repositories.stream()
                .mapToInt(r -> pullRequestRepository.findByRepository(r).size())
                .sum();

        RecommendedRepositoryDto recommendation =
                recommendationService.recommend(user)
                        .stream()
                        .findFirst()
                        .map(RepositoryRecommendation::toDto)
                        .orElse(null);

        WeeklyAchievementResponse weeklySummary =
                weeklyService.findLatest(user)
                        .map(WeeklyAchievementResponse::from)
                        .orElse(null);

        LinkedInPostResponse linkedInPost =
                linkedInService.findLatestByUser(user)
                        .map(LinkedInPostResponse::from)
                        .orElse(null);

        List<AchievementTimelineResponse> achievements =
                achievementTimelineService.timeline(user);

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
