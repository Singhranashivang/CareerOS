package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.cluster.CommitCluster;
import com.careeros.backend.achievement.cluster.CommitClusterer;
import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.AchievementConfidenceGate;
import com.careeros.backend.achievement.engine.AchievementFabricationValidator;
import com.careeros.backend.achievement.engine.AchievementSemanticDedupeValidator;
import com.careeros.backend.achievement.engine.AchievementTitleSpecificityValidator;
import com.careeros.backend.achievement.engine.DismissedAreaOverlapGate;
import com.careeros.backend.achievement.engine.EvidenceSufficiency;
import com.careeros.backend.achievement.engine.GroundingValidator;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.filter.CommitFilter;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.RepositoryAnalysisRecorder;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AchievementGeneratorServiceTest {

    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final CommitClusterer commitClusterer = mock(CommitClusterer.class);
    private final EvidenceBuilder evidenceBuilder = mock(EvidenceBuilder.class);
    private final RepositoryKnowledgeService repositoryKnowledgeService = mock(RepositoryKnowledgeService.class);
    private final AchievementPromptBuilder achievementPromptBuilder = mock(AchievementPromptBuilder.class);
    private final LLMService llmService = mock(LLMService.class);
    private final AchievementConfidenceCalculator confidenceCalculator = mock(AchievementConfidenceCalculator.class);
    private final AchievementConfidenceGate confidenceGate = mock(AchievementConfidenceGate.class);
    private final DismissedAreaOverlapGate dismissedAreaOverlapGate = mock(DismissedAreaOverlapGate.class);
    private final EvidenceSufficiency evidenceSufficiency = mock(EvidenceSufficiency.class);
    private final AchievementPersistenceService achievementPersistenceService = mock(AchievementPersistenceService.class);
    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final RepositoryAnalysisRecorder analysisRecorder = mock(RepositoryAnalysisRecorder.class);

    private final AchievementGeneratorService service = new AchievementGeneratorService(
            githubCommitRepository,
            new CommitFilter(), // pure logic, no reason to mock it
            commitClusterer,
            evidenceBuilder,
            repositoryKnowledgeService,
            achievementPromptBuilder,
            llmService,
            confidenceCalculator,
            confidenceGate,
            new GroundingValidator(), // pure logic, no reason to mock it
            new AchievementFabricationValidator(), // pure logic, no reason to mock it
            new AchievementSemanticDedupeValidator(), // pure logic, no reason to mock it
            new AchievementTitleSpecificityValidator(), // pure logic, no reason to mock it
            dismissedAreaOverlapGate,
            evidenceSufficiency,
            achievementPersistenceService,
            achievementRepository,
            analysisRecorder,
            // Matches Spring Boot's autoconfigured ObjectMapper bean (the one
            // actually injected in production), which disables this feature —
            // a vanilla `new ObjectMapper()` throws on unknown fields instead of
            // silently dropping them, which would test the wrong code path.
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

    {
        // @Value field — only set by Spring's container; the direct
        // construction above needs it set explicitly or it defaults to 0,
        // which silently empties every cluster list via subList(0, 0).
        ReflectionTestUtils.setField(service, "maxClustersPerRun", 5);
    }

    private static final GithubRepository REPO = GithubRepository.builder()
            .id(4L)
            .name("CareerOS")
            .fullName("Singhranashivang/CareerOS")
            .build();

    private static final GithubCommit COMMIT = GithubCommit.builder()
            .githubCommitSha("abc1234def5678")
            .message("Implement spiral search algorithm")
            .build();

    /** One commit is enough for these tests — CommitClusterer's own grouping logic has its own test. */
    private static final CommitCluster CLUSTER = new CommitCluster(List.of(COMMIT), Map.of());

    /** Real evidence content — GroundingValidator needs actual filenames/messages, not a mock. */
    private static Evidence groundedEvidence() {
        return Evidence.builder()
                .changedFiles(List.of("src/main/java/SpiralSearch.java"))
                .features(List.of(Feature.builder()
                        .name("Feature Development")
                        .evidence(List.of("Implement spiral search algorithm for 2D arrays"))
                        .build()))
                .build();
    }

    private void stubUpTo(Evidence evidence, String llmResponse) {
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of(CLUSTER));
        when(evidenceBuilder.buildForCluster(eq(REPO), eq(CLUSTER), any())).thenReturn(evidence);
        when(evidenceSufficiency.shortfall(any())).thenReturn(Optional.empty());
        when(repositoryKnowledgeService.generate(any(), any())).thenReturn(mock(RepositoryKnowledge.class));
        when(achievementPromptBuilder.build(any(), any(), any(), any())).thenReturn("prompt");
        when(achievementPromptBuilder.buildShortened(any(), any(), any(), any())).thenReturn("shortened-prompt");
        when(llmService.generate("prompt")).thenReturn(llmResponse);
        when(llmService.generate("shortened-prompt")).thenReturn(llmResponse);
    }

    @Test
    void schemaDriftOnBothAttemptsIsRejectedAsErrorAfterOneRetry() {
        // What Ollama actually returned for CareerOS: a plausible-looking but
        // entirely different schema, not our achievement shape and not
        // {"insufficient": true, ...} either.
        stubUpTo(mock(Evidence.class), """
                {"title":"GitHub Committer","description":"Fixed a bug","points":50}
                """);

        assertThatThrownBy(() -> service.generate(REPO, "token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no supporting content");

        // Both the normal and the shortened prompt were tried, in that order.
        verify(llmService).generate("prompt");
        verify(llmService).generate("shortened-prompt");
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ERROR),
                contains("no supporting content"));
        verifyNoInteractions(achievementPersistenceService);
    }

    @Test
    void schemaDriftOnTheFirstAttemptRecoversOnTheShortenedRetry() {
        Evidence evidence = groundedEvidence();
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of(CLUSTER));
        when(evidenceBuilder.buildForCluster(eq(REPO), eq(CLUSTER), any())).thenReturn(evidence);
        when(evidenceSufficiency.shortfall(any())).thenReturn(Optional.empty());
        when(repositoryKnowledgeService.generate(any(), any())).thenReturn(mock(RepositoryKnowledge.class));
        when(achievementPromptBuilder.build(any(), any(), any(), any())).thenReturn("prompt");
        when(achievementPromptBuilder.buildShortened(any(), any(), any(), any())).thenReturn("shortened-prompt");
        // First attempt drifts (no title); the shortened retry gets a real one.
        when(llmService.generate("prompt")).thenReturn("""
                {"achievement":{"title":"Achievement Title"}}
                """);
        when(llmService.generate("shortened-prompt")).thenReturn("""
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isFalse();
        assertThat(output.getTitle()).isEqualTo("Spiral Search Implementation");
        verify(llmService).generate("prompt");
        verify(llmService).generate("shortened-prompt");
        verify(achievementPersistenceService).saveEntity(any());
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ACHIEVEMENT), any());
    }

    @Test
    void aWellFormedClaimThatMatchesNoEvidenceIsRejectedAsUngrounded() {
        stubUpTo(groundedEvidence(), """
                {"title":"Cloud Infrastructure Migration",
                 "resumeBullet":"Migrated production workloads to Kubernetes clusters",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);

        assertThatThrownBy(() -> service.generate(REPO, "token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not grounded");

        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ERROR), contains("not grounded"));
        verifyNoInteractions(achievementPersistenceService);
    }

    @Test
    void aClaimBelowTheConfidenceFloorIsRecordedInsufficientNotPersisted() {
        stubUpTo(groundedEvidence(), """
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.3);
        when(confidenceGate.passes(0.3)).thenReturn(false);
        when(confidenceGate.reasonBelowThreshold(0.3)).thenReturn("Computed confidence 0.30 is below the 0.50 threshold");

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isTrue();
        assertThat(output.getReason()).contains("0.30");
        verify(analysisRecorder).record(4L, AnalysisOutcome.INSUFFICIENT,
                "No cluster produced a grounded achievement (1 cluster(s) analysed)");
        verifyNoInteractions(achievementPersistenceService);
    }

    @Test
    void aGroundedClaimAboveTheFloorIsPersisted() {
        stubUpTo(groundedEvidence(), """
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isFalse();
        assertThat(output.getTitle()).isEqualTo("Spiral Search Implementation");
        // The LLM response above self-reported 0.95; the returned output must
        // carry the calculated 0.8 instead — the same value persisted onto
        // the entity, not the model's own guess.
        assertThat(output.getConfidence()).isEqualTo(0.8);
        verify(achievementPersistenceService).saveEntity(any());
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ACHIEVEMENT), any());
    }

    @Test
    void aVagueTitleTriggersOneRetryNamingTheProblemThenSucceeds() {
        stubUpTo(groundedEvidence(), """
                {"title":"GitHub Committer",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(achievementPromptBuilder.buildRetryForVagueTitle(
                any(), any(), any(), any(), eq("GitHub Committer"), any()))
                .thenReturn("vague-title-retry-prompt");
        when(llmService.generate("vague-title-retry-prompt")).thenReturn("""
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isFalse();
        assertThat(output.getTitle()).isEqualTo("Spiral Search Implementation");
        verify(llmService).generate("prompt");
        verify(llmService).generate("vague-title-retry-prompt");
        verify(achievementPersistenceService).saveEntity(any());
    }

    @Test
    void aVagueTitleThatStaysVagueAfterRetryIsRejectedAsAnError() {
        stubUpTo(groundedEvidence(), """
                {"title":"GitHub Committer",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(achievementPromptBuilder.buildRetryForVagueTitle(
                any(), any(), any(), any(), eq("GitHub Committer"), any()))
                .thenReturn("vague-title-retry-prompt");
        when(llmService.generate("vague-title-retry-prompt")).thenReturn("""
                {"title":"Code Contributor",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);

        assertThatThrownBy(() -> service.generate(REPO, "token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("still names no file, class, method, or technology");

        verify(llmService).generate("prompt");
        verify(llmService).generate("vague-title-retry-prompt");
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ERROR), contains("still names no file"));
        verifyNoInteractions(achievementPersistenceService);
    }

    @Test
    void theClustersChangedFilePathsArePersistedOnTheEntityGeneratedFilesExcluded() {
        var file = new com.careeros.backend.github.dto.GithubCommitFileResponse();
        file.setFilename("src/main/java/SpiralSearch.java");
        var lockfile = new com.careeros.backend.github.dto.GithubCommitFileResponse();
        lockfile.setFilename("package-lock.json");
        CommitCluster clusterWithFiles = new CommitCluster(
                List.of(COMMIT), Map.of(COMMIT.getGithubCommitSha(), List.of(file, lockfile)));

        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of(clusterWithFiles));
        when(evidenceBuilder.buildForCluster(eq(REPO), eq(clusterWithFiles), any())).thenReturn(groundedEvidence());
        when(evidenceSufficiency.shortfall(any())).thenReturn(Optional.empty());
        when(repositoryKnowledgeService.generate(any(), any())).thenReturn(mock(RepositoryKnowledge.class));
        when(achievementPromptBuilder.build(any(), any(), any(), any())).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);

        service.generate(REPO, "token");

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.careeros.backend.achievement.record.AchievementEntity.class);
        verify(achievementPersistenceService).saveEntity(captor.capture());
        assertThat(captor.getValue().getFilePathsJson())
                .contains("src/main/java/SpiralSearch.java")
                .doesNotContain("package-lock.json");
    }

    @Test
    void aNearParaphraseTriggersARegenerationAttemptBeforeRejecting() {
        stubUpTo(groundedEvidence(), """
                {"title":"Spiral Search Implementation",
                 "resumeBullet":"Implemented a spiral search algorithm for 2D arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);
        // A different cluster, weeks earlier, already described the same work.
        var existingAchievement = com.careeros.backend.achievement.record.AchievementEntity.builder()
                .title("Spiral Search Algorithm Implementation")
                .resumeBullet("Built a spiral search algorithm")
                .build();
        when(achievementRepository.findByRepositoryIdOrderByGeneratedAtDesc(4L))
                .thenReturn(List.of(existingAchievement));

        AchievementOutput output = service.generate(REPO, "token").get(0);

        // The regeneration attempt (the "describe only what's new" retry) got
        // the same content back, so the fallback reject fires — but a second
        // model call was made, not an immediate reject.
        assertThat(output.isInsufficient()).isTrue();
        assertThat(output.getReason()).containsIgnoringCase("still").contains("Spiral Search Algorithm Implementation");
        verify(llmService, times(2)).generate("prompt");
        verifyNoInteractions(achievementPersistenceService);
    }

    @Test
    void aRegenerationThatDescribesSomethingNewIsPersistedInstead() {
        stubUpTo(groundedEvidence(), "unused"); // overridden below with two distinct responses
        // First call duplicates prior work; the "describe only what's new"
        // retry comes back about a different mechanism entirely, still
        // grounded (via "SpiralSearch.java" in the resumeBullet) but sharing
        // no title vocabulary and little resumeBullet vocabulary with the
        // prior achievement.
        when(llmService.generate("prompt")).thenReturn(
                """
                {"title":"Spiral Search Algorithm Implementation",
                 "resumeBullet":"Built the initial spiral search algorithm implementation for two dimensional arrays",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """,
                """
                {"title":"Added Recursion Depth Limit In Java",
                 "resumeBullet":"Added a recursion depth limit to SpiralSearch.java to stop stack overflow on deeply nested grids",
                 "starSituation":"x","starTask":"x","starAction":"x","starResult":"x",
                 "confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJson(any(), any(), any()))
                .thenReturn(false);
        var existingAchievement = com.careeros.backend.achievement.record.AchievementEntity.builder()
                .title("Spiral Search Algorithm Implementation")
                .resumeBullet("Built the initial spiral search algorithm implementation for two dimensional arrays")
                .build();
        when(achievementRepository.findByRepositoryIdOrderByGeneratedAtDesc(4L))
                .thenReturn(List.of(existingAchievement));

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isFalse();
        assertThat(output.getTitle()).isEqualTo("Added Recursion Depth Limit In Java");
        // Confidence comes from the retried response's title/bullet path, but
        // must still be the calculated value, not the retry's own self-reported 0.95.
        assertThat(output.getConfidence()).isEqualTo(0.8);
        verify(llmService, times(2)).generate("prompt");
        verify(achievementPersistenceService).saveEntity(any());
    }

    @Test
    void aClusterMatchingADismissedAchievementIsSkippedWithoutCallingTheModel() {
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of(CLUSTER));
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJsonAndDismissedTrue(
                any(), any(), any())).thenReturn(true);

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isTrue();
        assertThat(output.getReason()).containsIgnoringCase("dismissed");
        verifyNoInteractions(llmService, evidenceBuilder, achievementPersistenceService);
    }

    @Test
    void aClusterSubstantiallyOverlappingRepeatedDismissalsIsSkippedWithoutCallingTheModel() {
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of(CLUSTER));
        when(achievementRepository.existsByUserAndRepositoryNameAndCitedCommitShasJsonAndDismissedTrue(
                any(), any(), any())).thenReturn(false);
        when(dismissedAreaOverlapGate.reasonToSkip(any(), any(), any()))
                .thenReturn(Optional.of("substantially overlaps 2 previously dismissed clusters"));

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isTrue();
        assertThat(output.getReason()).containsIgnoringCase("overlaps 2 previously dismissed");
        verifyNoInteractions(llmService, evidenceBuilder, achievementPersistenceService);
    }

    @Test
    void noClustersProducesAnInsufficientResultWithoutCallingTheModel() {
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of());

        AchievementOutput output = service.generate(REPO, "token").get(0);

        assertThat(output.isInsufficient()).isTrue();
        verifyNoInteractions(llmService);
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.INSUFFICIENT), any());
    }

    /** Same protection the weekly and star paths already had — see WeeklyAchievementService/StarStoryService. */
    @Test
    void mergeAndTrivialCommitsAreFilteredOutBeforeClustering() {
        GithubCommit real = GithubCommit.builder().message("Add spiral search algorithm").build();
        GithubCommit merge = GithubCommit.builder().message("Merge pull request #12 from feature/x").build();
        GithubCommit typo = GithubCommit.builder().message("fix typo in README").build();
        when(githubCommitRepository.findByRepository(REPO)).thenReturn(List.of(real, merge, typo));
        when(commitClusterer.cluster(eq(REPO), any(), any())).thenReturn(List.of());

        service.generate(REPO, "token");

        verify(commitClusterer).cluster(eq(REPO), eq(List.of(real)), any());
    }
}
