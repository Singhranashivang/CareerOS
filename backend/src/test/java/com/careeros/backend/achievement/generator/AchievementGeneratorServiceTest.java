package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.AchievementConfidenceGate;
import com.careeros.backend.achievement.engine.EvidenceSufficiency;
import com.careeros.backend.achievement.engine.GroundingValidator;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.RepositoryAnalysisRecorder;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AchievementGeneratorServiceTest {

    private final EvidenceBuilder evidenceBuilder = mock(EvidenceBuilder.class);
    private final RepositoryKnowledgeService repositoryKnowledgeService = mock(RepositoryKnowledgeService.class);
    private final AchievementPromptBuilder achievementPromptBuilder = mock(AchievementPromptBuilder.class);
    private final LLMService llmService = mock(LLMService.class);
    private final AchievementConfidenceCalculator confidenceCalculator = mock(AchievementConfidenceCalculator.class);
    private final AchievementConfidenceGate confidenceGate = mock(AchievementConfidenceGate.class);
    private final EvidenceSufficiency evidenceSufficiency = mock(EvidenceSufficiency.class);
    private final AchievementPersistenceService achievementPersistenceService = mock(AchievementPersistenceService.class);
    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final RepositoryAnalysisRecorder analysisRecorder = mock(RepositoryAnalysisRecorder.class);

    private final AchievementGeneratorService service = new AchievementGeneratorService(
            mock(GithubCommitRepository.class),
            mock(GithubPullRequestRepository.class),
            evidenceBuilder,
            repositoryKnowledgeService,
            achievementPromptBuilder,
            llmService,
            confidenceCalculator,
            confidenceGate,
            new GroundingValidator(), // pure logic, no reason to mock it
            evidenceSufficiency,
            achievementPersistenceService,
            achievementRepository,
            analysisRecorder,
            // Matches Spring Boot's autoconfigured ObjectMapper bean (the one
            // actually injected in production), which disables this feature —
            // a vanilla `new ObjectMapper()` throws on unknown fields instead of
            // silently dropping them, which would test the wrong code path.
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

    private static final GithubRepository REPO = GithubRepository.builder()
            .id(4L)
            .name("CareerOS")
            .fullName("Singhranashivang/CareerOS")
            .build();

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
        when(evidenceBuilder.build(eq(REPO), any(), any(), any())).thenReturn(evidence);
        when(evidenceSufficiency.shortfall(any())).thenReturn(Optional.empty());
        when(repositoryKnowledgeService.generate(any(), any())).thenReturn(mock(RepositoryKnowledge.class));
        when(achievementPromptBuilder.build(any(), any())).thenReturn("prompt");
        when(achievementPromptBuilder.buildShortened(any(), any())).thenReturn("shortened-prompt");
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
                .hasMessageContaining("missing required fields");

        // Both the normal and the shortened prompt were tried, in that order.
        verify(llmService).generate("prompt");
        verify(llmService).generate("shortened-prompt");
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ERROR),
                contains("missing required fields"));
        verifyNoInteractions(achievementRepository);
    }

    @Test
    void schemaDriftOnTheFirstAttemptRecoversOnTheShortenedRetry() {
        Evidence evidence = groundedEvidence();
        when(evidenceBuilder.build(eq(REPO), any(), any(), any())).thenReturn(evidence);
        when(evidenceSufficiency.shortfall(any())).thenReturn(Optional.empty());
        when(repositoryKnowledgeService.generate(any(), any())).thenReturn(mock(RepositoryKnowledge.class));
        when(achievementPromptBuilder.build(any(), any())).thenReturn("prompt");
        when(achievementPromptBuilder.buildShortened(any(), any())).thenReturn("shortened-prompt");
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
        when(achievementRepository.existsByUserAndRepositoryNameAndTitle(any(), any(), any()))
                .thenReturn(false);

        AchievementOutput output = service.generate(REPO, "token");

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
        verifyNoInteractions(achievementRepository);
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

        AchievementOutput output = service.generate(REPO, "token");

        assertThat(output.isInsufficient()).isTrue();
        assertThat(output.getReason()).contains("0.30");
        verify(analysisRecorder).record(4L, AnalysisOutcome.INSUFFICIENT,
                "Computed confidence 0.30 is below the 0.50 threshold");
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
        when(achievementRepository.existsByUserAndRepositoryNameAndTitle(any(), any(), any()))
                .thenReturn(false);

        AchievementOutput output = service.generate(REPO, "token");

        assertThat(output.isInsufficient()).isFalse();
        assertThat(output.getTitle()).isEqualTo("Spiral Search Implementation");
        verify(achievementPersistenceService).saveEntity(any());
        verify(analysisRecorder).record(eq(4L), eq(AnalysisOutcome.ACHIEVEMENT), any());
    }
}
