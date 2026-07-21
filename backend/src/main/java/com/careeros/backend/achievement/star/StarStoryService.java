package com.careeros.backend.achievement.star;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.filter.CommitFilter;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StarStoryService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    private final CommitFilter commitFilter;
    private final EvidenceBuilder evidenceBuilder;

    private final StarPromptBuilder starPromptBuilder;
    private final LLMService llmService;

    private final ObjectMapper objectMapper;

    public StarStory generate(User user) {

        var repository = githubRepositoryRepository.findByUser(user)
                .stream()
                .filter(r -> r.getName().equalsIgnoreCase("CareerOS"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CareerOS repository not found"));

        var commits = commitFilter.filter(
                githubCommitRepository.findByRepository(repository)
        );

        var pullRequests =
                githubPullRequestRepository.findByRepository(repository);

        Evidence evidence = evidenceBuilder.build(
                repository,
                commits,
                pullRequests
        );

        try {

            System.out.println("\n========== STAR EVIDENCE ==========");
            System.out.println(
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(evidence)
            );
            System.out.println("===================================\n");

        } catch (Exception ignored) {
        }

        String prompt = starPromptBuilder.build(evidence);

        System.out.println("\n========== STAR PROMPT ==========");
        System.out.println(prompt);
        System.out.println("=================================\n");

        String response = llmService.generate(prompt);

        System.out.println("\n========== STAR RESPONSE ==========");
        System.out.println(response);
        System.out.println("==================================\n");

        try {

            return objectMapper.readValue(
                    response,
                    StarStory.class
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse STAR story",
                    e
            );

        }

    }
}