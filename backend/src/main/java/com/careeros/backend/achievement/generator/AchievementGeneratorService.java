package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.achievement.record.AchievementSource;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementGeneratorService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final EvidenceBuilder evidenceBuilder;
    private final RepositoryKnowledgeService repositoryKnowledgeService;

    private final AchievementPromptBuilder achievementPromptBuilder;
    private final LLMService llmService;

    private final AchievementConfidenceCalculator confidenceCalculator;
    private final AchievementPersistenceService achievementPersistenceService;
    private final AchievementRepository achievementRepository;

    private final ObjectMapper objectMapper;

    @Transactional
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

        String prompt = achievementPromptBuilder.build(knowledge, evidence);

        log.debug("Achievement prompt for {}:\n{}", repository.getFullName(), prompt);

        String response = llmService.generate(prompt);

        log.debug("Achievement response for {}:\n{}", repository.getFullName(), response);

        AchievementOutput output;
        try {
            output = objectMapper.readValue(response, AchievementOutput.class);
        } catch (Exception e) {
            log.error("LLM returned unparseable achievement for {}",
                    repository.getFullName(), e);
            throw new RuntimeException(
                    "Failed to parse achievement for " + repository.getFullName(), e);
        }

        // A null title can't be deduped and can't be rendered — reject it here
        // rather than storing another untitled row.
        if (output.getTitle() == null || output.getTitle().isBlank()) {
            throw new RuntimeException(
                    "LLM returned an achievement with no title for "
                            + repository.getFullName());
        }

        // Regenerating on an already-analysed repo is normal; a duplicate row
        // is not. The unique index would throw here, so check first and return
        // the existing result instead of failing the request.
        if (achievementRepository.existsByUserAndRepositoryNameAndTitle(
                repository.getUser(), repository.getName(), output.getTitle())) {

            log.info("Achievement '{}' already exists for {} — skipping save",
                    output.getTitle(), repository.getFullName());
            return output;
        }

        // Scored from the evidence, not from the model's opinion of itself.
        double confidence = confidenceCalculator.calculate(evidence);

        log.info("Generated achievement for {} with confidence {}",
                repository.getFullName(), confidence);

        AchievementEntity entity = AchievementEntity.builder()
                .user(repository.getUser())
                .repository(repository)
                .repositoryName(repository.getName())
                .source(AchievementSource.GITHUB)
                .title(output.getTitle())
                .resumeBullet(output.getResumeBullet())
                .starSituation(output.getStarSituation())
                .starTask(output.getStarTask())
                .starAction(output.getStarAction())
                .starResult(output.getStarResult())
                .confidence(confidence)
                .generatedAt(LocalDateTime.now())
                .build();

        achievementPersistenceService.saveEntity(entity);

        return output;
    }
}