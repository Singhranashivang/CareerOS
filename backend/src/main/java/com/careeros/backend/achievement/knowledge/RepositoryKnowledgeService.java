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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositoryKnowledgeService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final EvidenceBuilder evidenceBuilder;
    private final KnowledgePromptBuilder knowledgePromptBuilder;
    private final LLMService llmService;

    private final RepositoryKnowledgePersistenceService repositoryKnowledgePersistenceService;

    private final ObjectMapper objectMapper;

    public RepositoryKnowledge generate(GithubRepository repository) {

        var existing =
                repositoryKnowledgePersistenceService.findByRepository(repository);

        if (existing.isPresent()) {

            RepositoryKnowledgeEntity entity = existing.get();

            try {

                return RepositoryKnowledge.builder()
                        .repositoryName(repository.getName())
                        .projectType(entity.getProjectType())
                        .domain(entity.getDomain())
                        .technologies(
                                objectMapper.readValue(
                                        entity.getTechnologiesJson(),
                                        objectMapper.getTypeFactory()
                                                .constructCollectionType(
                                                        List.class,
                                                        String.class
                                                )
                                )
                        )
                        .architecture(
                                objectMapper.readValue(
                                        entity.getArchitectureJson(),
                                        objectMapper.getTypeFactory()
                                                .constructCollectionType(
                                                        List.class,
                                                        String.class
                                                )
                                )
                        )
                        .features(
                                objectMapper.readValue(
                                        entity.getFeaturesJson(),
                                        objectMapper.getTypeFactory()
                                                .constructCollectionType(
                                                        List.class,
                                                        String.class
                                                )
                                )
                        )
                        .developerContributions(
                                objectMapper.readValue(
                                        entity.getDeveloperContributionsJson(),
                                        objectMapper.getTypeFactory()
                                                .constructCollectionType(
                                                        List.class,
                                                        String.class
                                                )
                                )
                        )
                        .confidence(entity.getConfidence())
                        .build();

            } catch (Exception e) {

                throw new RuntimeException(
                        "Failed to read cached repository knowledge",
                        e
                );

            }

        }

        var commits =
                githubCommitRepository.findByRepository(repository);

        var pullRequests =
                githubPullRequestRepository.findByRepository(repository);

        Evidence evidence =
                evidenceBuilder.build(
                        repository,
                        commits,
                        pullRequests
                );

        String prompt =
                knowledgePromptBuilder.build(evidence);

        System.out.println("========== KNOWLEDGE PROMPT ==========");
        System.out.println(prompt);
        System.out.println("======================================");

        String response =
                llmService.generate(prompt);

        System.out.println("========== KNOWLEDGE RESPONSE ==========");
        System.out.println(response);
        System.out.println("========================================");

        try {

            RepositoryKnowledge knowledge =
                    objectMapper.readValue(
                            response,
                            RepositoryKnowledge.class
                    );

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
                    "Failed to generate repository knowledge",
                    e
            );

        }

    }

}