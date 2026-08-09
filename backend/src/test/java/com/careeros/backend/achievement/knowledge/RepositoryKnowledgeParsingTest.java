package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.achievement.engine.Achievement;
import com.careeros.backend.achievement.weekly.WeeklySummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryKnowledgeParsingTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private RepositoryKnowledge parse(String architectureJson) throws Exception {
        return objectMapper.readValue("""
                {"repositoryName":"CareerOS","projectType":"Application","domain":"Careers",
                 "technologies":["Java"],"architecture":%s,
                 "features":[],"developerContributions":[],"confidence":0.9}
                """.formatted(architectureJson), RepositoryKnowledge.class);
    }

    @Test
    void readsTheSchemaShape() throws Exception {
        assertThat(parse("[\"Controller\",\"Service\"]").getArchitecture())
                .containsExactly("Controller", "Service");
    }

    @Test
    void readsTheLabelledObjectShape() throws Exception {
        var knowledge = parse(
                "{\"type\":\"Layered Architecture\",\"layers\":[\"Controller\",\"Service\",\"Repository\"]}");

        assertThat(knowledge.getArchitecture())
                .containsExactly("Layered Architecture", "Controller", "Service", "Repository");
    }

    @Test
    void readsABareString() throws Exception {
        assertThat(parse("\"Layered Architecture\"").getArchitecture())
                .containsExactly("Layered Architecture");
    }

    @Test
    void readsNullAsEmptyRatherThanThrowing() throws Exception {
        assertThat(parse("null").getArchitecture()).isEmpty();
    }

    @Test
    void readsAMixedArray() throws Exception {
        assertThat(parse("[\"Controller\",{\"layer\":\"Service\"}]").getArchitecture())
                .containsExactly("Controller", "Service");
    }

    // ---- the shapes that broke AchievementPromptBuilder:97 ----

    @Test
    void readsMajorFeaturesUnderItsAlias() throws Exception {
        var knowledge = objectMapper.readValue("""
                {"repositoryName":"CareerOS","projectType":"Application","domain":"Careers",
                 "major_features":["Achievement engine","Post scheduling"],
                 "confidence":0.9}
                """, RepositoryKnowledge.class);

        assertThat(knowledge.getFeatures())
                .containsExactly("Achievement engine", "Post scheduling");
    }

    @Test
    void readsBooleanFlagObjectByItsKeys() throws Exception {
        // {"key": true} carries its content in the keys, not the values.
        var knowledge = objectMapper.readValue("""
                {"repositoryName":"CareerOS",
                 "developer_contributions":{"Built the sync":true,"Wrote docs":true,"Unrelated":false},
                 "confidence":0.9}
                """, RepositoryKnowledge.class);

        assertThat(knowledge.getDeveloperContributions())
                .containsExactly("Built the sync", "Wrote docs");
    }

    @Test
    void everyListIsEmptyRatherThanNullWhenKeysAreMissing() throws Exception {
        var knowledge = objectMapper.readValue(
                "{\"projectType\":\"Application\"}", RepositoryKnowledge.class);

        assertThat(knowledge.getTechnologies()).isEmpty();
        assertThat(knowledge.getArchitecture()).isEmpty();
        assertThat(knowledge.getFeatures()).isEmpty();
        assertThat(knowledge.getDeveloperContributions()).isEmpty();
    }

    // ---- the same guarantee on the other model-authored types ----

    @Test
    void achievementListsAreNeverNull() throws Exception {
        Achievement achievement = objectMapper.readValue(
                "{\"title\":\"Built a thing\"}", Achievement.class);

        assertThat(achievement.getTechnologies()).isEmpty();
        assertThat(achievement.getEvidence()).isEmpty();
    }

    @Test
    void achievementListsTolerateObjects() throws Exception {
        Achievement achievement = objectMapper.readValue("""
                {"title":"Built a thing","technologies":{"backend":["Java"],"db":"Postgres"}}
                """, Achievement.class);

        assertThat(achievement.getTechnologies()).containsExactly("Java", "Postgres");
    }

    @Test
    void weeklySummaryListsAreNeverNull() throws Exception {
        WeeklySummary summary = objectMapper.readValue(
                "{\"title\":\"A week\"}", WeeklySummary.class);

        assertThat(summary.getHighlights()).isEmpty();
        assertThat(summary.getTechnologies()).isEmpty();
    }
}
