package com.careeros.backend.achievement.weekly;

import com.careeros.backend.achievement.engine.AchievementResult;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeeklyAchievementService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final WeeklyPromptBuilder weeklyPromptBuilder;
    private final LLMService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AchievementResult generateWeeklyAchievement(User user) {

        var repositories = githubRepositoryRepository.findByUser(user);

        var commits = repositories.stream()
                .flatMap(repo -> githubCommitRepository.findByRepository(repo).stream())
                .toList();

        var pullRequests = repositories.stream()
                .flatMap(repo -> githubPullRequestRepository.findByRepository(repo).stream())
                .toList();

        WeeklyAchievementInput input = WeeklyAchievementInput.builder()
                .repositories(repositories)
                .commits(commits)
                .pullRequests(pullRequests)
                .build();

        String prompt = weeklyPromptBuilder.buildPrompt(input);

        String response = llmService.generate(prompt);

        try {

            return objectMapper.readValue(
                    response,
                    AchievementResult.class
            );

        } catch (Exception e) {

            throw new RuntimeException("Failed to parse Ollama response", e);

        }

    }
}