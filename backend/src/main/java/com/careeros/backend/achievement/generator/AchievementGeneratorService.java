package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.repositoryachievement.RepositoryAchievementEntity;
import com.careeros.backend.achievement.repositoryachievement.RepositoryAchievementPersistenceService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AchievementGeneratorService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final EvidenceBuilder evidenceBuilder;
    private final RepositoryKnowledgeService repositoryKnowledgeService;

    private final AchievementPromptBuilder achievementPromptBuilder;
    private final LLMService llmService;

    private final RepositoryAchievementPersistenceService repositoryAchievementPersistenceService;

    private final ObjectMapper objectMapper;

    public AchievementOutput generate(GithubRepository repository) {

        var commits = githubCommitRepository.findByRepository(repository);

        var pullRequests = githubPullRequestRepository.findByRepository(repository);

        Evidence evidence = evidenceBuilder.build(
                repository,
                commits,
                pullRequests
        );

        RepositoryKnowledge knowledge =
                repositoryKnowledgeService.generate(repository);

        String prompt = achievementPromptBuilder.build(
                knowledge,
                evidence
        );

        System.out.println("========== ACHIEVEMENT PROMPT ==========");
        System.out.println(prompt);
        System.out.println("========================================");

        String response = llmService.generate(prompt);

        System.out.println("========== ACHIEVEMENT RESPONSE ==========");
        System.out.println(response);
        System.out.println("==========================================");

        try {

            AchievementOutput output =
                    objectMapper.readValue(
                            response,
                            AchievementOutput.class
                    );

            RepositoryAchievementEntity entity =
                    RepositoryAchievementEntity.builder()
                            .repository(repository)
                            .title(output.getTitle())
                            .resumeBullet(output.getResumeBullet())
                            .starSituation(output.getStarSituation())
                            .starTask(output.getStarTask())
                            .starAction(output.getStarAction())
                            .starResult(output.getStarResult())
                            .confidence(output.getConfidence())
                            .generatedAt(LocalDateTime.now())
                            .build();

            repositoryAchievementPersistenceService.save(entity);

            return output;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse achievement",
                    e
            );

        }
    }
}