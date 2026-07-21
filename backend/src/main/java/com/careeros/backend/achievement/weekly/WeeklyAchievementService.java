package com.careeros.backend.achievement.weekly;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.achievement.engine.AchievementEngine;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.filter.CommitFilter;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendationService;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementEntity;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementPersistenceService;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyAchievementService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final CommitFilter commitFilter;
    private final EvidenceBuilder evidenceBuilder;

    private final AchievementEngine achievementEngine;
    private final AchievementPersistenceService achievementPersistenceService;

    private final WeeklyPromptBuilder weeklyPromptBuilder;
    private final LLMService llmService;

    private final WeeklyAchievementPersistenceService weeklyAchievementPersistenceService;

    private final RepositoryRecommendationService repositoryRecommendationService;

    private final ObjectMapper objectMapper;

    public WeeklySummary generate(User user) {

        /*
        var existing = weeklyAchievementPersistenceService.findByUser(user);

        if (existing.isPresent()) {

            WeeklyAchievementEntity entity = existing.get();

            try {

                return WeeklySummary.builder()
                        .title(entity.getTitle())
                        .summary(entity.getSummary())
                        .highlights(
                                objectMapper.readValue(
                                        entity.getHighlightsJson(),
                                        new TypeReference<List<String>>() {}
                                )
                        )
                        .technologies(
                                objectMapper.readValue(
                                        entity.getTechnologiesJson(),
                                        new TypeReference<List<String>>() {}
                                )
                        )
                        .confidence(entity.getConfidence())
                        .build();

            } catch (Exception e) {
                throw new RuntimeException("Failed to read cached weekly summary", e);
            }
        }
        */

        RepositoryRecommendation recommendation =
                repositoryRecommendationService
                        .recommend(user)
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException("No repositories found"));

        var repository = recommendation.getRepository();

        System.out.println("\n======= RECOMMENDED REPOSITORY =======");
        System.out.println("Repository : " + repository.getName());
        System.out.println("Score      : " + recommendation.getScore());
        System.out.println("\nReasons:");
        recommendation.getReasons()
                .forEach(reason -> System.out.println("• " + reason));
        System.out.println("======================================");

        var commits = commitFilter.filter(
                githubCommitRepository.findByRepository(repository)
        );

        System.out.println("\n========== COMMITS ==========");
        System.out.println("Commit count = " + commits.size());

        commits.forEach(commit ->
                System.out.println(commit.getMessage()));

        System.out.println("=============================\n");

        var pullRequests =
                githubPullRequestRepository.findByRepository(repository);

        Evidence evidence = evidenceBuilder.build(
                repository,
                commits,
                pullRequests
        );

        try {

            System.out.println("\n========== EVIDENCE ==========");

            System.out.println(
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(evidence)
            );

            System.out.println("==============================\n");

        } catch (Exception ignored) {
        }

        // ==========================================
        // Generate Achievements
        // ==========================================

        List<Achievement> achievements =
                achievementEngine.generate(evidence);

        try {

            System.out.println("\n========== ACHIEVEMENTS ==========");

            for (Achievement achievement : achievements) {

                System.out.println(
                        objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(achievement)
                );

                achievementPersistenceService.save(
                        user,
                        achievement
                );
            }

            System.out.println("==================================\n");

        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to save achievements",
                    e
            );
        }

        // ==========================================
        // Weekly Summary
        // ==========================================

        String prompt = weeklyPromptBuilder.build(evidence);

        System.out.println("\n========== WEEKLY PROMPT ==========");
        System.out.println(prompt);
        System.out.println("===================================\n");

        String response = llmService.generate(prompt);

        System.out.println("\n========== WEEKLY RESPONSE ==========");
        System.out.println(response);
        System.out.println("=====================================\n");

        try {

            WeeklySummary summary = objectMapper.readValue(
                    response,
                    WeeklySummary.class
            );

            WeeklyAchievementEntity entity =
                    WeeklyAchievementEntity.builder()
                            .user(user)
                            .title(summary.getTitle())
                            .summary(summary.getSummary())
                            .highlightsJson(
                                    objectMapper.writeValueAsString(
                                            summary.getHighlights()
                                    )
                            )
                            .technologiesJson(
                                    objectMapper.writeValueAsString(
                                            summary.getTechnologies()
                                    )
                            )
                            .confidence(summary.getConfidence())
                            .generatedAt(LocalDateTime.now())
                            .build();

            weeklyAchievementPersistenceService.save(entity);

            return summary;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse weekly summary",
                    e
            );

        }
    }
}