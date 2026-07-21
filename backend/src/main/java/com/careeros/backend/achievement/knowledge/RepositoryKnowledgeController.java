package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgeEntity;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgePersistenceService;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class RepositoryKnowledgeController {

    private final RepositoryKnowledgeService repositoryKnowledgeService;
    private final RepositoryKnowledgePersistenceService repositoryKnowledgePersistenceService;

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final UserRepository userRepository;

    @GetMapping("/generate")
    public RepositoryKnowledge generate() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow();

        var repository = githubRepositoryRepository.findByUser(user)
                .stream()
                .findFirst()
                .orElseThrow();

        return repositoryKnowledgeService.generate(repository);
    }

    @GetMapping("/{repositoryId}")
    public RepositoryKnowledgeEntity getKnowledge(
            @PathVariable Long repositoryId
    ) {

        return repositoryKnowledgePersistenceService
                .findByRepositoryId(repositoryId)
                .orElseThrow(() ->
                        new RuntimeException("Knowledge not found"));

    }
}