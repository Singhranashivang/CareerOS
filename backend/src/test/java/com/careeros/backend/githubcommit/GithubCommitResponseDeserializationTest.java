package com.careeros.backend.githubcommit;

import com.careeros.backend.githubcommit.dto.GithubCommitResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a real GitHub payload captured from
 * /repos/Singhranashivang/AI-Assistant/commits, so the mapping is checked
 * against the actual field names rather than a hand-written approximation.
 */
class GithubCommitResponseDeserializationTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private List<GithubCommitResponse> parseFixture() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/github/commits-list.json")) {
            assertThat(in).as("fixture present").isNotNull();
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    @Test
    void readsTheTopLevelAuthorAccount() throws Exception {

        GithubCommitResponse commit = parseFixture().get(0);

        assertThat(commit.getSha()).startsWith("c656d3b");
        assertThat(commit.getAuthor())
                .as("top-level author object must deserialize")
                .isNotNull();
        assertThat(commit.getAuthor().getId()).isEqualTo(92661186L);
        assertThat(commit.getAuthor().getLogin()).isEqualTo("Singhranashivang");
    }

    @Test
    void stillReadsTheGitAuthorAndHtmlUrl() throws Exception {

        GithubCommitResponse commit = parseFixture().get(0);

        assertThat(commit.getCommit().getMessage())
                .isEqualTo("Initial commit: AI Assistant frontend and backend");
        assertThat(commit.getCommit().getAuthor().getEmail())
                .isEqualTo("ranashivang567@gmail.com");
        assertThat(commit.getHtmlUrl()).contains("/commit/c656d3b");
    }
}
