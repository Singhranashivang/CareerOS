package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.AchievementConfidenceGate;
import com.careeros.backend.achievement.engine.EvidenceSufficiency;
import com.careeros.backend.achievement.engine.GroundingValidator;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.filter.CommitFilter;
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

    private final CommitFilter commitFilter;
    private final EvidenceBuilder evidenceBuilder;
    private final RepositoryKnowledgeService repositoryKnowledgeService;

    private final AchievementPromptBuilder achievementPromptBuilder;
    private final LLMService llmService;

    private final AchievementConfidenceCalculator confidenceCalculator;
    private final AchievementConfidenceGate confidenceGate;
    private final GroundingValidator groundingValidator;
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

        var commits = commitFilter.filter(githubCommitRepository.findByRepository(repository));
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

        // One retry, with a shortened prompt, before giving up. Schema drift
        // on a large repo was caused by Ollama truncating the prompt to its
        // context window — the shortened prompt has much smaller evidence
        // caps, so it needs far fewer tokens even if num_ctx still isn't
        // enough for the full one.
        AchievementOutput output;
        try {
            output = requestAchievement(repository, knowledge, evidence, false);
        } catch (SchemaDriftException first) {
            log.warn("Schema drift for {} ({}) — retrying once with a shortened prompt",
                    repository.getFullName(), first.getMessage());
            output = requestAchievement(repository, knowledge, evidence, true);
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

        // The shape is right, but is it actually about this repository? A
        // model that ignores "never invent" can produce a well-formed STAR
        // story for work that never happened. If nothing in the title or
        // resumeBullet matches a filename or commit message, there's no
        // basis to believe it's grounded in this evidence rather than
        // hallucinated — reject it the same as a schema failure.
        var ungroundedReason = groundingValidator.ungroundedReason(
                output.getTitle(), output.getResumeBullet(), evidence);
        if (ungroundedReason.isPresent()) {
            log.warn("Achievement response for {} is not grounded in the evidence: {}. "
                            + "title=\"{}\" resumeBullet=\"{}\"",
                    repository.getFullName(), ungroundedReason.get(),
                    output.getTitle(), output.getResumeBullet());
            throw new RuntimeException(
                    "the model's claim is not grounded in the evidence: " + ungroundedReason.get());
        }

        // Scored from the evidence, not from the model's opinion of itself.
        double confidence = confidenceCalculator.calculate(evidence);

        // Below the floor: this isn't a failure, the model just didn't have
        // enough independent evidence backing it up. Same treatment as the
        // evidence-sufficiency shortfall above — INSUFFICIENT, not ERROR.
        if (!confidenceGate.passes(confidence)) {
            String reason = confidenceGate.reasonBelowThreshold(confidence);
            log.info("Not persisting achievement for {}: {}", repository.getFullName(), reason);
            analysisRecorder.record(repository.getId(), AnalysisOutcome.INSUFFICIENT, reason);
            return AchievementOutput.builder()
                    .insufficient(true)
                    .reason(reason)
                    .confidence(confidence)
                    .build();
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

    /**
     * One request/parse/validate cycle. Throws SchemaDriftException (never a
     * plain RuntimeException) for both "not JSON" and "JSON but not our
     * shape" — the two things a shortened prompt might actually fix — so the
     * caller can tell those apart from a grounding failure, which it can't.
     */
    private AchievementOutput requestAchievement(
            GithubRepository repository,
            RepositoryKnowledge knowledge,
            Evidence evidence,
            boolean shortened
    ) {

        String prompt = shortened
                ? achievementPromptBuilder.buildShortened(knowledge, evidence)
                : achievementPromptBuilder.build(knowledge, evidence);

        log.debug("Achievement prompt for {}:\n{}", repository.getFullName(), prompt);

        String response = llmService.generate(prompt);

        log.debug("Achievement response for {}:\n{}", repository.getFullName(), response);

        AchievementOutput output;
        try {
            output = objectMapper.readValue(response, AchievementOutput.class);
        } catch (Exception e) {
            log.warn("Achievement response for {} could not be parsed as JSON. Raw response:\n{}",
                    repository.getFullName(), response);
            throw new SchemaDriftException(
                    "the model returned a response that could not be read", e);
        }

        // The model's own refusal isn't drift — it read the schema fine and
        // chose the documented escape hatch. Let the caller handle it.
        if (output.isInsufficient()) {
            return output;
        }

        // Reject anything short of the full shape rather than storing a row with
        // some fields blank. Checking title alone let a real case through: on a
        // large repo (176 files / ~10k lines here) the model sometimes abandons
        // our schema entirely and returns its own unrelated JSON shape instead
        // — {"title": "GitHub Committer", "description": ..., "points": 50} —
        // which happens to have a non-blank "title" and nothing else we need, so
        // it parsed clean, wasn't flagged insufficient, and slipped past the old
        // check into a persisted achievement with an empty resume bullet and
        // empty STAR fields. Genuinely thin evidence goes through the
        // insufficient branch instead; this is the model missing the contract.
        if (isBlank(output.getTitle()) || isBlank(output.getResumeBullet())
                || isBlank(output.getStarSituation()) || isBlank(output.getStarTask())
                || isBlank(output.getStarAction()) || isBlank(output.getStarResult())) {
            log.warn("Achievement response for {} did not match the required schema "
                            + "(missing title and/or resumeBullet/STAR fields). Raw response:\n{}",
                    repository.getFullName(), response);
            throw new SchemaDriftException(
                    "the model returned an achievement missing required fields");
        }

        return output;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Message a user could read, rather than a stack frame. */
    private static String describe(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }
}
