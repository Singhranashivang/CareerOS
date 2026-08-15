package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.llm.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AchievementBuilderTest {

    private final LLMService llmService = mock(LLMService.class);
    private final AchievementConfidenceCalculator confidenceCalculator = mock(AchievementConfidenceCalculator.class);
    private final AchievementConfidenceGate confidenceGate = mock(AchievementConfidenceGate.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private final AchievementBuilder builder = new AchievementBuilder(
            new AchievementEnginePromptBuilder(),
            new EvidenceSufficiency(),
            confidenceCalculator,
            confidenceGate,
            new GroundingValidator(), // pure logic, no reason to mock it
            llmService,
            objectMapper);

    private static Evidence evidence(int commits, int files, int added) {
        return evidence(commits, files, added, List.of());
    }

    /** Real evidence content — GroundingValidator needs actual filenames/messages, not a mock. */
    private static Evidence evidence(int commits, int files, int added, List<Feature> features) {
        return Evidence.builder()
                .repositoryName("Programming_Hactoberfest25")
                .description("Hacktoberfest repo")
                .features(features)
                .changedFiles(List.of())
                .changedFileInsights(List.of())
                .technologies(List.of())
                .codeStats(CodeStats.builder()
                        .commitCount(commits)
                        .filesTouched(files)
                        .linesAdded(added)
                        .build())
                .build();
    }

    @Test
    void belowTheFloorReturnsNothingAndNeverCallsTheModel() {

        List<Achievement> result = builder.build(evidence(1, 2, 30));

        assertThat(result).isEmpty();
        verify(llmService, never()).generate(any());
    }

    @Test
    void modelDeclaringInsufficientReturnsNothing() {

        when(llmService.generate(anyString())).thenReturn(
                "{\"insufficient\": true, \"reason\": \"only a README edit\"}");

        assertThat(builder.build(evidence(5, 10, 400))).isEmpty();
    }

    private static final List<Feature> GROUNDED_FEATURES = List.of(Feature.builder()
            .name("Commit Sync")
            .evidence(List.of("Added author filtering to commit sync pipeline"))
            .build());

    @Test
    void aGroundedAchievementAboveTheConfidenceFloorComesBack() {

        when(llmService.generate(anyString())).thenReturn("""
                {"title":"Built the commit ingestion pipeline",
                 "summary":"I added author filtering to commit sync.",
                 "highlights":[],"technologies":["Java"],"confidence":0.8}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.7);
        when(confidenceGate.passes(0.7)).thenReturn(true);

        List<Achievement> result = builder.build(evidence(5, 10, 400, GROUNDED_FEATURES));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Built the commit ingestion pipeline");
        assertThat(result.get(0).isInsufficient()).isFalse();
        // Computed, not the model's self-reported 0.8.
        assertThat(result.get(0).getConfidence()).isEqualTo(0.7);
    }

    @Test
    void belowTheConfidenceFloorReturnsNothingEvenWhenTheModelDidNotDecline() {

        when(llmService.generate(anyString())).thenReturn("""
                {"title":"Built the commit ingestion pipeline",
                 "summary":"I added author filtering to commit sync.",
                 "highlights":[],"technologies":["Java"],"confidence":0.8}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.2);
        when(confidenceGate.passes(0.2)).thenReturn(false);
        when(confidenceGate.reasonBelowThreshold(0.2)).thenReturn("Computed confidence 0.20 is below the 0.50 threshold");

        assertThat(builder.build(evidence(5, 10, 400, GROUNDED_FEATURES))).isEmpty();
    }

    /** Same protection the per-repo generator already had — see AchievementGeneratorServiceTest. */
    @Test
    void aWellFormedClaimThatMatchesNoEvidenceIsRejectedAsUngroundedBeforeScoring() {

        when(llmService.generate(anyString())).thenReturn("""
                {"title":"Cloud Infrastructure Migration",
                 "summary":"Migrated production workloads to Kubernetes clusters",
                 "highlights":[],"technologies":["Kubernetes"],"confidence":0.95}
                """);

        assertThat(builder.build(evidence(5, 10, 400))).isEmpty();

        // Rejected on grounding, before it ever reached scoring.
        verifyNoInteractions(confidenceCalculator);
        verifyNoInteractions(confidenceGate);
    }
}
