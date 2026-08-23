package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.cluster.CommitCluster;
import com.careeros.backend.achievement.cluster.CommitClusterer;
import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.AchievementConfidenceGate;
import com.careeros.backend.achievement.engine.AchievementFabricationValidator;
import com.careeros.backend.achievement.engine.AchievementSemanticDedupeValidator;
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
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementGeneratorService {

    private final GithubCommitRepository githubCommitRepository;

    private final CommitFilter commitFilter;
    private final CommitClusterer commitClusterer;
    private final EvidenceBuilder evidenceBuilder;
    private final RepositoryKnowledgeService repositoryKnowledgeService;

    private final AchievementPromptBuilder achievementPromptBuilder;
    private final LLMService llmService;

    private final AchievementConfidenceCalculator confidenceCalculator;
    private final AchievementConfidenceGate confidenceGate;
    private final GroundingValidator groundingValidator;
    private final AchievementFabricationValidator fabricationValidator;
    private final AchievementSemanticDedupeValidator semanticDedupeValidator;
    private final EvidenceSufficiency evidenceSufficiency;
    private final AchievementPersistenceService achievementPersistenceService;
    private final AchievementRepository achievementRepository;
    private final RepositoryAnalysisRecorder analysisRecorder;

    private final ObjectMapper objectMapper;

    /** Keeps one large repository from turning a single analyze click into an hour-long run. */
    @Value("${app.achievement.cluster.max-per-run:5}")
    private int maxClustersPerRun;

    /**
     * Every exit records an outcome on the repository. Without that, a repo that
     * was analysed and declined looks exactly like one never analysed, and the
     * Analyze button appears to do nothing.
     */
    @Transactional
    public List<AchievementOutput> generate(GithubRepository repository, String accessToken) {

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

    /**
     * The unit of reasoning is a commit cluster, not the repository: one LLM
     * call per cluster, so a repository can produce several achievements or
     * none. Clusters come back ordered most-recent-first (CommitClusterer);
     * maxClustersPerRun caps how many of those get analysed.
     */
    private List<AchievementOutput> analyse(GithubRepository repository, String accessToken) {

        var commits = commitFilter.filter(githubCommitRepository.findByRepository(repository));
        var clusters = commitClusterer.cluster(repository, commits, accessToken);

        if (clusters.isEmpty()) {
            String reason = "No group of commits met the clustering threshold "
                    + "(time proximity + shared source area)";
            log.info("No achievement for {}: {}", repository.getFullName(), reason);
            analysisRecorder.record(repository.getId(), AnalysisOutcome.INSUFFICIENT, reason);
            return List.of(AchievementOutput.builder().insufficient(true).reason(reason).build());
        }

        var toAnalyze = clusters.size() > maxClustersPerRun
                ? clusters.subList(0, maxClustersPerRun)
                : clusters;

        // Repository-level context (project type, domain, tech stack) is shared
        // across every cluster in this run — one call, not one per cluster.
        RepositoryKnowledge knowledge = repositoryKnowledgeService.generate(repository, accessToken);

        List<AchievementOutput> outputs = new ArrayList<>();
        int achievementCount = 0;

        for (CommitCluster cluster : toAnalyze) {
            AchievementOutput output = analyseCluster(repository, knowledge, cluster, accessToken);
            outputs.add(output);
            if (!output.isInsufficient()) {
                achievementCount++;
            }
        }

        String summary = achievementCount > 0
                ? "Generated %d achievement(s) from %d cluster(s) (%d commits analysed)".formatted(
                        achievementCount, toAnalyze.size(), commits.size())
                : "No cluster produced a grounded achievement (%d cluster(s) analysed)".formatted(
                        toAnalyze.size());

        analysisRecorder.record(repository.getId(),
                achievementCount > 0 ? AnalysisOutcome.ACHIEVEMENT : AnalysisOutcome.INSUFFICIENT,
                summary);

        return outputs;
    }

    /**
     * One cluster through the full pipeline: evidence, sufficiency floor, LLM
     * call (with the one-retry-on-drift already used per repository),
     * grounding, confidence gate, dedupe on the cluster's own commit set, and
     * persistence. Never throws for an ordinary "nothing here" outcome — only
     * for a grounding failure, which the caller (and its @Transactional
     * rollback + ERROR outcome) already treats as a real failure.
     */
    private AchievementOutput analyseCluster(
            GithubRepository repository,
            RepositoryKnowledge knowledge,
            CommitCluster cluster,
            String accessToken
    ) {
        String label = repository.getFullName() + " cluster " + clusterLabel(cluster);
        String citedShasJson = citedCommitShasJson(cluster);

        // Checked before any evidence-building or LLM work: a dismissed
        // achievement must never be regenerated, not just never re-saved.
        if (achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJsonAndDismissedTrue(
                repository.getUser(), repository.getName(), citedShasJson)) {

            log.info("Skipping {} — commit set matches a dismissed achievement", label);
            return AchievementOutput.builder()
                    .insufficient(true).reason("Matches a dismissed achievement").build();
        }

        Evidence evidence = evidenceBuilder.buildForCluster(repository, cluster, accessToken);

        var shortfall = evidenceSufficiency.shortfall(evidence);
        if (shortfall.isPresent()) {
            log.info("No achievement for {}: {}", label, shortfall.get());
            return AchievementOutput.builder().insufficient(true).reason(shortfall.get()).build();
        }

        AchievementOutput output = requestAndValidate(repository, knowledge, evidence, label, null, null);
        if (output.isInsufficient()) {
            return output;
        }

        // Evidence-derived, not output-derived — the same value regardless of
        // which attempt (first pass or the "describe only what's new" retry
        // below) ends up being persisted, so this only needs computing once.
        double confidence = confidenceCalculator.calculate(evidence);

        if (!confidenceGate.passes(confidence)) {
            String reason = confidenceGate.reasonBelowThreshold(confidence);
            log.info("Not persisting achievement for {}: {}", label, reason);
            return AchievementOutput.builder()
                    .insufficient(true).reason(reason).confidence(confidence).build();
        }

        // Dedup on the cluster's commit set rather than title: regenerating a
        // repo re-clusters the same commits into the same groups, and the
        // model can phrase the same cluster differently on each attempt.
        if (achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(
                repository.getUser(), repository.getName(), citedShasJson)) {

            log.info("Achievement for {} already exists for this commit set — skipping save", label);
            return output;
        }

        // Exact-SHA dedup only catches the SAME cluster regenerated. It can't
        // catch two different clusters, weeks apart, that both touch the same
        // subsystem — commit-set dedup sees no overlap there at all. Compare
        // text against every existing achievement for this repository instead.
        var existingForRepository = repository.getId() == null
                ? List.<AchievementEntity>of()
                : achievementRepository.findByRepositoryIdOrderByGeneratedAtDesc(repository.getId());

        var semanticDuplicate = semanticDedupeValidator.duplicateOf(
                output.getTitle(), output.getResumeBullet(), existingForRepository);

        if (semanticDuplicate.isPresent()) {
            AchievementEntity priorWork = semanticDuplicate.get();

            // Not an automatic reject: the same subsystem worked on twice,
            // weeks apart, is two real achievements, not one. Give the model
            // the prior achievement and ask it to describe only what's new;
            // only reject if it still can't produce anything distinct.
            log.info("Achievement for {} overlaps existing \"{}\" — regenerating to describe only what's new",
                    label, priorWork.getTitle());

            AchievementOutput retried = requestAndValidate(
                    repository, knowledge, evidence, label, priorWork.getTitle(), priorWork.getResumeBullet());

            if (retried.isInsufficient()) {
                // The model itself found nothing new relative to the prior work.
                return retried;
            }

            var stillDuplicate = semanticDedupeValidator.duplicateOf(
                    retried.getTitle(), retried.getResumeBullet(), existingForRepository);

            if (stillDuplicate.isPresent()) {
                String reason = "Still substantially overlaps an existing achievement after describing "
                        + "only what's new: \"" + stillDuplicate.get().getTitle() + "\"";
                log.info("Not persisting achievement for {}: {}", label, reason);
                return AchievementOutput.builder()
                        .insufficient(true).reason(reason).confidence(confidence).build();
            }

            output = retried;
        }

        log.info("Generated achievement for {} with confidence {}", label, confidence);

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
                .citedCommitShasJson(citedShasJson)
                .technologiesJson(technologiesJson(evidence))
                .confidence(confidence)
                .generatedAt(LocalDateTime.now())
                .build();

        achievementPersistenceService.saveEntity(entity);

        return output;
    }

    /**
     * Was never populated for GitHub-sourced achievements before this —
     * evidence.getTechnologies() was already computed during evidence
     * building and simply wasn't carried onto the entity, so "distinct
     * technologies" had nothing to read across a repository's achievements.
     */
    private String technologiesJson(Evidence evidence) {
        try {
            return objectMapper.writeValueAsString(evidence.getTechnologies());
        } catch (Exception e) {
            log.warn("Failed to serialize technologies for evidence, storing none", e);
            return null;
        }
    }

    /** Sorted so the same commit set always serializes to the same string — the dedup key. */
    private String citedCommitShasJson(CommitCluster cluster) {
        try {
            List<String> shas = cluster.commits().stream()
                    .map(GithubCommit::getGithubCommitSha)
                    .sorted()
                    .toList();
            return objectMapper.writeValueAsString(shas);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cluster commit SHAs", e);
        }
    }

    private static String clusterLabel(CommitCluster cluster) {
        return cluster.commits().isEmpty()
                ? "(empty)"
                : cluster.commits().get(cluster.size() - 1).getGithubCommitSha().substring(0, 7)
                        + " (" + cluster.size() + " commits)";
    }

    /**
     * requestAchievement (with its own one-retry-on-schema-drift), then the
     * insufficient passthrough, grounding, and fabrication checks. priorTitle/
     * priorResumeBullet, when non-null, make this the "describe only what's
     * new relative to prior work" regeneration rather than the first attempt
     * — see AchievementPromptBuilder.
     */
    private AchievementOutput requestAndValidate(
            GithubRepository repository,
            RepositoryKnowledge knowledge,
            Evidence evidence,
            String label,
            String priorTitle,
            String priorResumeBullet
    ) {
        AchievementOutput output;
        try {
            output = requestAchievement(repository, knowledge, evidence, false, priorTitle, priorResumeBullet);
        } catch (SchemaDriftException first) {
            log.warn("Schema drift for {} ({}) — retrying once with a shortened prompt",
                    label, first.getMessage());
            output = requestAchievement(repository, knowledge, evidence, true, priorTitle, priorResumeBullet);
        }

        if (output.isInsufficient()) {
            String reason = output.getReason() == null || output.getReason().isBlank()
                    ? "The evidence did not support a specific achievement"
                    : output.getReason();
            log.info("Model declined to claim an achievement for {}: {}", label, reason);
            output.setReason(reason);
            return output;
        }

        var ungroundedReason = groundingValidator.ungroundedReason(output, evidence);
        if (ungroundedReason.isPresent()) {
            log.warn("Achievement response for {} is not grounded in the evidence: {}. "
                            + "title=\"{}\" resumeBullet=\"{}\"",
                    label, ungroundedReason.get(), output.getTitle(), output.getResumeBullet());
            throw new RuntimeException(
                    "the model's claim is not grounded in the evidence: " + ungroundedReason.get());
        }

        // Grounding catches "shares no word with the evidence"; this catches
        // the subtler failure of a claim that DOES share words but still
        // invents a team's reaction or a technology never used — see
        // AchievementFabricationValidator.
        var fabricationReason = fabricationValidator.fabricationReason(output, evidence);
        if (fabricationReason.isPresent()) {
            log.warn("Achievement response for {} looks fabricated: {}. title=\"{}\"",
                    label, fabricationReason.get(), output.getTitle());
            throw new RuntimeException(
                    "the model's claim looks fabricated: " + fabricationReason.get());
        }

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
            boolean shortened,
            String priorTitle,
            String priorResumeBullet
    ) {

        String prompt = shortened
                ? achievementPromptBuilder.buildShortened(knowledge, evidence, priorTitle, priorResumeBullet)
                : achievementPromptBuilder.build(knowledge, evidence, priorTitle, priorResumeBullet);

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

        // Every field but title is now genuinely optional — the model is
        // instructed to omit whatever the evidence doesn't support (see
        // AchievementPromptBuilder). So the schema-drift check can no longer
        // require all six fields; it only needs to catch the model
        // abandoning our schema entirely, which is: no title, or a title
        // with literally nothing else — every other field blank too. That
        // second case is exactly the historical bug this replaced: on a
        // large repo (176 files / ~10k lines here) the model sometimes
        // returned its own unrelated JSON shape instead —
        // {"title": "GitHub Committer", "description": ..., "points": 50} —
        // which has a non-blank title and nothing else we need. A response
        // that legitimately omits starSituation/starTask because the
        // evidence doesn't support them still has a resumeBullet or
        // starAction; only "title and nothing else" indicates drift.
        if (isBlank(output.getTitle())) {
            log.warn("Achievement response for {} did not match the required schema "
                            + "(missing title). Raw response:\n{}",
                    repository.getFullName(), response);
            throw new SchemaDriftException("the model returned an achievement with no title");
        }

        boolean hasSubstance = !isBlank(output.getResumeBullet())
                || !isBlank(output.getStarSituation())
                || !isBlank(output.getStarTask())
                || !isBlank(output.getStarAction())
                || !isBlank(output.getStarResult());

        if (!hasSubstance) {
            log.warn("Achievement response for {} had a title but no supporting content in any "
                            + "other field. Raw response:\n{}",
                    repository.getFullName(), response);
            throw new SchemaDriftException(
                    "the model returned a title but no supporting content in any other field");
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
