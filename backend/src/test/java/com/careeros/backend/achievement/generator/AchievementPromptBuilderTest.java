package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AchievementPromptBuilderTest {

    private final AchievementPromptBuilder builder = new AchievementPromptBuilder();

    private static RepositoryKnowledge emptyKnowledge() {
        RepositoryKnowledge knowledge = mock(RepositoryKnowledge.class);
        when(knowledge.getTechnologies()).thenReturn(List.of());
        when(knowledge.getArchitecture()).thenReturn(List.of());
        when(knowledge.getFeatures()).thenReturn(List.of());
        when(knowledge.getDeveloperContributions()).thenReturn(List.of());
        return knowledge;
    }

    /** Ollama truncates the prompt from the front — the schema has to survive at the tail. */
    @Test
    void theJsonSchemaComesAfterTheEvidenceNotBeforeIt() {
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("Foo.java"))
                .build();

        String prompt = builder.build(emptyKnowledge(), evidence);

        int evidenceIndex = prompt.indexOf("Changed Files:");
        int schemaIndex = prompt.indexOf("\"resumeBullet\"");

        assertThat(evidenceIndex).isPositive();
        assertThat(schemaIndex).isGreaterThan(evidenceIndex);
    }

    @Test
    void changedFilesBeyondTheCapAreSummarisedNotListed() {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            files.add("File" + i + ".java");
        }
        Evidence evidence = Evidence.builder().changedFiles(files).build();

        String prompt = builder.build(emptyKnowledge(), evidence);

        assertThat(prompt).contains("File0.java").contains("File29.java");
        assertThat(prompt).doesNotContain("File30.java");
        assertThat(prompt).contains("...and 20 more files");
    }

    @Test
    void shortenedBuildCapsMuchMoreAggressivelyThanTheNormalBuild() {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            files.add("File" + i + ".java");
        }
        Evidence evidence = Evidence.builder().changedFiles(files).build();

        String shortened = builder.buildShortened(emptyKnowledge(), evidence);

        assertThat(shortened).contains("File9.java");
        assertThat(shortened).doesNotContain("File10.java");
        assertThat(shortened.length()).isLessThan(builder.build(emptyKnowledge(), evidence).length());
    }

    @Test
    void aSmallEvidenceSetIsListedInFullWithNoSummaryLine() {
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("Foo.java", "Bar.java"))
                .features(List.of(Feature.builder()
                        .name("Feature Development")
                        .evidence(List.of("Added Foo", "Added Bar"))
                        .build()))
                .build();

        String prompt = builder.build(emptyKnowledge(), evidence);

        assertThat(prompt).contains("Foo.java").contains("Bar.java");
        assertThat(prompt).doesNotContain("more file").doesNotContain("more commit");
    }
}
