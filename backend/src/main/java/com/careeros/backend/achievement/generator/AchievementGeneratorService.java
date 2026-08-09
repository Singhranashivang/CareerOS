package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.EvidenceSufficiency;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.achievement.record.AchievementSource;
import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.RepositoryAnalysisRecorder;
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
    private final EvidenceSufficiency evidenceSufficiency;
    private final AchievementPersistenceService achievementPersistenceService;
    private final AchievementRepository achievementRepository;
    private final RepositoryAnalysisRecorder analysisRecorder;

    private final ObjectMapper objectMapper;

    /**
     * Every exit records an outcome on the repository. Without that, a repo that
     * was analysed and declined looks exactly like one never analysed, and the
     * Analyze button appears to do nothing.
     */
    @Transactional
    public AchievementOutput generate(GithubRepository repository, String accessToken) {

        try {
            return analyse(repository, accessToken);

        } catch (Exception e) {
            log.error("Analysis failed for {}", repository.getFullName(), e);
            analysisRecorder.record(repository.getId(), AnalysisOutcome.ERROR,
                    "Analysis failed: " + describe(e));
            throw e instanceof RuntimeException runtime
                    ? runtime
                    : new RuntimeException("Analysis failed for "
                            + repository.getFullName(), e);
        }
    }

    private AchievementOutput analyse(GithubRepository repository, String accessToken) {

        var commits = githubCommitRepository.findByRepository(repository);
        var pullRequests = githubPullRequestRepository.findByRepository(repository);

        Evidence evidence = evidenceBuilder.build(
                repository,
                commits,
                pullRequests,
                accessToken
        );

        // Before any LLM call, including the knowledge one below. A repository
        // the user barely touched has nothing to claim, and asking anyway just
        // produces a paraphrase of its name.
        var shortfall = evidenceSufficiency.shortfall(evidence);
        if (shortfall.isPresent()) {
            log.info("No achievement for {}: {}",
                    repository.getFullName(), shortfall.get());
            analysisRecorder.record(repository.getId(),
                    AnalysisOutcome.INSUFFICIENT, shortfall.get());
            return AchievementOutput.builder()
                    .insufficient(true)
                    .reason(shortfall.get())
                    .build();
        }

        RepositoryKnowledge knowledge =
                repositoryKnowledgeService.generate(repository, accessToken);

        String prompt = achievementPromptBuilder.build(knowledge, evidence);

        log.debug("Achievement prompt for {}:\n{}", repository.getFullName(), prompt);

        String response = llmService.generate(prompt);

        log.debug("Achievement response for {}:\n{}", repository.getFullName(), response);

        AchievementOutput output;
        try {
            output = objectMapper.readValue(response, AchievementOutput.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "the model returned a response that could not be read", e);
        }

        // The model's own refusal. A normal outcome — return it unpersisted so
        // the caller can distinguish "analysed, nothing to claim" from a failure.
        if (output.isInsufficient()) {
            String reason = output.getReason() == null || output.getReason().isBlank()
                    ? "The evidence did not support a specific achievement"
                    : output.getReason();
            log.info("Model declined to claim an achievement for {}: {}",
                    repository.getFullName(), reason);
            analysisRecorder.record(repository.getId(),
                    AnalysisOutcome.INSUFFICIENT, reason);
            return output;
        }

        // A null title can't be deduped and can't be rendered — reject it here
        // rather than storing another untitled row.
        if (output.getTitle() == null || output.getTitle().isBlank()) {
            throw new RuntimeException("the model returned an achievement with no title");
        }

        // Regenerating on an already-analysed repo is normal; a duplicate row
        // is not. The unique index would throw here, so check first and return
        // the existing result instead of failing the request.
        if (achievementRepository.existsByUserAndRepositoryNameAndTitle(
                repository.getUser(), repository.getName(), output.getTitle())) {

            log.info("Achievement '{}' already exists for {} — skipping save",
                    output.getTitle(), repository.getFullName());
            analysisRecorder.record(repository.getId(), AnalysisOutcome.ACHIEVEMENT,
                    "Already recorded: " + output.getTitle());
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

        analysisRecorder.record(repository.getId(), AnalysisOutcome.ACHIEVEMENT,
                "Generated: " + output.getTitle());

        return output;
    }

    /** Message a user could read, rather than a stack frame. */
    private static String describe(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }
}
