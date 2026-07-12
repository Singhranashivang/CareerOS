package com.careeros.backend.achievement;

import com.careeros.backend.achievement.engine.AchievementEngine;
import com.careeros.backend.achievement.engine.AchievementInput;
import com.careeros.backend.achievement.engine.AchievementResult;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final AchievementEngine achievementEngine;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    public void generateAchievements(User user) {

        var repositories = githubRepositoryRepository.findByUser(user);

        System.out.println("Repositories: " + repositories.size());

        for (var repository : repositories) {

            var commits = githubCommitRepository.findByRepository(repository);

            var pullRequests = githubPullRequestRepository.findByRepository(repository);

            var input = AchievementInput.builder()
                    .repository(repository)
                    .commits(commits)
                    .pullRequests(pullRequests)
                    .build();

            AchievementResult result = achievementEngine.generate(input);

            Achievement achievement = Achievement.builder()
                    .user(user)
                    .title(result.getTitle())
                    .description(result.getDescription())
                    .confidence(result.getConfidence())
                    .status("PENDING")
                    .evidence(
                            "Repository: " + repository.getName()
                                    + "\nCommits: " + commits.size()
                                    + "\nPull Requests: " + pullRequests.size()
                    )
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            achievementRepository.save(achievement);

            System.out.println("Generated Achievement:");
            System.out.println(result.getTitle());
        }
    }

}