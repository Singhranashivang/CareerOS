package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgeEntity;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgePersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryKnowledgeService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final EvidenceBuilder evidenceBuilder;
    private final KnowledgePromptBuilder knowledgePromptBuilder;
    private final LLMService llmService;

    private final RepositoryKnowledgePersistenceService repositoryKnowledgePersistenceService;

    private final ObjectMapper objectMapper;

    public RepositoryKnowledge generate(GithubRepository repository, String accessToken) {

        var existing =
                repositoryKnowledgePersistenceService.findByRepository(repository);

        if (existing.isPresent()) {
            return toKnowledge(repository, existing.get());
        }

        var commits =
                githubCommitRepository.findByRepository(repository);

        var pullRequests =
                githubPullRequestRepository.findByRepository(repository);

        Evidence evidence =
                evidenceBuilder.build(
                        repository,
                        commits,
                        pullRequests,
                        accessToken
                );

        String prompt =
                knowledgePromptBuilder.build(evidence);

        log.debug("Knowledge prompt for {}:\n{}", repository.getFullName(), prompt);

        String response =
                llmService.generate(prompt);

        log.debug("Knowledge response for {}:\n{}", repository.getFullName(), response);

        RepositoryKnowledge knowledge;
        try {
            knowledge = objectMapper.readValue(response, RepositoryKnowledge.class);

        } catch (Exception e) {
            // The raw response is the only way to tell which field the model
            // mangled; without it this failure is undiagnosable after the fact.
            log.error("Could not read repository knowledge for {}. Raw response was:\n{}",
                    repository.getFullName(), response, e);

            // Message is surfaced as the ERROR analysis_reason, so it names the
            // problem rather than the stack frame.
            throw new RuntimeException(
                    "the model returned repository knowledge in an unreadable shape: "
                            + firstLine(e.getMessage()), e);
        }

        try {

            RepositoryKnowledgeEntity entity =
                    RepositoryKnowledgeEntity.builder()
                            .repository(repository)
                            .projectType(knowledge.getProjectType())
                            .domain(knowledge.getDomain())
                            .technologiesJson(
                                    objectMapper.writeValueAsString(
                                            knowledge.getTechnologies()
                                    )
                            )
                            .architectureJson(
                                    objectMapper.writeValueAsString(
                                            knowledge.getArchitecture()
                                    )
                            )
                            .featuresJson(
                                    objectMapper.writeValueAsString(
                                            knowledge.getFeatures()
                                    )
                            )
                            .developerContributionsJson(
                                    objectMapper.writeValueAsString(
                                            knowledge.getDeveloperContributions()
                                    )
                            )
                            .confidence(knowledge.getConfidence())
                            .generatedAt(LocalDateTime.now())
                            .build();

            repositoryKnowledgePersistenceService.save(entity);

            return knowledge;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to store repository knowledge for "
                            + repository.getFullName(),
                    e
            );

        }

    }

    /**
     * The stored-entity shape, converted to the same {@link RepositoryKnowledge}
     * the frontend gets from a fresh generation — never return
     * {@link RepositoryKnowledgeEntity} itself across an HTTP boundary,
     * lazy-proxy fields aside, it's the persistence type, not a response type.
     */
    public RepositoryKnowledge toKnowledge(GithubRepository repository, RepositoryKnowledgeEntity entity) {
        try {
            // Passing an explicit null to the builder overrides
            // @Builder.Default, so every list is read through readList().
            return RepositoryKnowledge.builder()
                    .repositoryName(repository.getName())
                    .projectType(entity.getProjectType())
                    .domain(entity.getDomain())
                    .technologies(readList(entity.getTechnologiesJson()))
                    .architecture(readList(entity.getArchitectureJson()))
                    .features(readList(entity.getFeaturesJson()))
                    .developerContributions(
                            readList(entity.getDeveloperContributionsJson()))
                    .confidence(entity.getConfidence())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to read cached repository knowledge for "
                            + repository.getFullName(),
                    e
            );
        }
    }

    /**
     * Cached knowledge predates the tolerant deserializer, so a stored column
     * can be null, blank, or literal "null". Any of those must read as an empty
     * list rather than null.
     */
    private List<String> readList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<String> values = objectMapper.readValue(
                json,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class));
        return values == null ? new ArrayList<>() : values;
    }

    /** Jackson messages carry a multi-line reference chain; the first line says what broke. */
    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "no detail available";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

}