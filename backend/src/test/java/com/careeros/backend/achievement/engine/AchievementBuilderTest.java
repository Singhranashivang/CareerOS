package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
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
import static org.mockito.Mockito.when;

class AchievementBuilderTest {

    private final LLMService llmService = mock(LLMService.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private final AchievementBuilder builder = new AchievementBuilder(
            new AchievementEnginePromptBuilder(),
            new EvidenceSufficiency(),
            llmService,
            objectMapper);

    private static Evidence evidence(int commits, int files, int added) {
        return Evidence.builder()
                .repositoryName("Programming_Hactoberfest25")
                .description("Hacktoberfest repo")
                .features(List.of())
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

    @Test
    void aGroundedAchievementStillComesBack() {

        when(llmService.generate(anyString())).thenReturn("""
                {"title":"Built the commit ingestion pipeline",
                 "summary":"I added author filtering to commit sync.",
                 "highlights":[],"technologies":["Java"],"confidence":0.8}
                """);

        List<Achievement> result = builder.build(evidence(5, 10, 400));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Built the commit ingestion pipeline");
        assertThat(result.get(0).isInsufficient()).isFalse();
    }
}
