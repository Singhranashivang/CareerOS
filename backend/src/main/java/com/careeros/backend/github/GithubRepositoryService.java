package com.careeros.backend.github;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GithubRepositoryService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiService githubApiService;

    public void syncRepositories(User user) {

        var repositories = githubApiService.getRepositories(
                user.getEncryptedGithubAccessToken()
        );

        System.out.println("Repositories found: " + repositories.size());

        repositories.forEach(repo -> {

            GithubRepository entity = githubRepositoryRepository
                    .findByGithubRepositoryId(repo.getId())
                    .orElse(new GithubRepository());

            entity.setUser(user);
            entity.setGithubRepositoryId(repo.getId());
            entity.setName(repo.getName());
            entity.setFullName(repo.getFullName());
            entity.setDescription(repo.getDescription());
            entity.setLanguage(repo.getLanguage());
            entity.setDefaultBranch(repo.getDefaultBranch());
            entity.setPrivateRepo(repo.getPrivateRepo());
            entity.setHtmlUrl(repo.getHtmlUrl());
            entity.setCreatedAtGithub(
                    LocalDateTime.parse(repo.getCreatedAt().replace("Z", ""))
            );
            entity.setUpdatedAtGithub(
                    LocalDateTime.parse(repo.getUpdatedAt().replace("Z", ""))
            );
            entity.setSyncedAt(LocalDateTime.now());

            githubRepositoryRepository.save(entity);
        });
    }
}