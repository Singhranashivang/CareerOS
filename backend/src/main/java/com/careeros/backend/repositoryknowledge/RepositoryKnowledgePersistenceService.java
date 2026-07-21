package com.careeros.backend.repositoryknowledge;

import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryKnowledgePersistenceService {

    private final RepositoryKnowledgeRepository repositoryKnowledgeRepository;

    public RepositoryKnowledgeEntity save(
            RepositoryKnowledgeEntity entity
    ) {

        return repositoryKnowledgeRepository.save(entity);
    }

    public Optional<RepositoryKnowledgeEntity> findByRepository(
            GithubRepository repository
    ) {
        return repositoryKnowledgeRepository.findByRepository(repository);
    }

    public Optional<RepositoryKnowledgeEntity> findByRepositoryId(
            Long repositoryId
    ) {
        return repositoryKnowledgeRepository.findByRepositoryId(repositoryId);
    }

    public void delete(
            RepositoryKnowledgeEntity entity
    ) {
        repositoryKnowledgeRepository.delete(entity);
    }
}