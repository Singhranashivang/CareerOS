package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgeEntity;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgePersistenceService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class RepositoryKnowledgeController {

    private final RepositoryKnowledgeService repositoryKnowledgeService;
    private final RepositoryKnowledgePersistenceService repositoryKnowledgePersistenceService;

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final CurrentUserService currentUserService;

    @GetMapping("/generate")
    public RepositoryKnowledge generate() {

        User user = currentUserService.require();

        var repository = githubRepositoryRepository.findByUser(user)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No repositories synced yet"));

        return repositoryKnowledgeService.generate(repository);
    }

    // Step 7 adds the ownership check here.
    @GetMapping("/{repositoryId}")
    public RepositoryKnowledgeEntity getKnowledge(@PathVariable Long repositoryId) {

        return repositoryKnowledgePersistenceService
                .findByRepositoryId(repositoryId)
                .orElseThrow(() -> new RuntimeException("Knowledge not found"));
    }
}