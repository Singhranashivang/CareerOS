package com.careeros.backend.github;

import com.careeros.backend.github.dto.GithubRepositoryResponse;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubRepositoryService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiService githubApiService;

    @Transactional(readOnly = true)
    public List<GithubRepository> findAllForUser(User user) {
        return githubRepositoryRepository.findByUserOrderByUpdatedAtGithubDesc(user);
    }

    @Transactional
    public int syncRepositories(User user) {

        var remote = githubApiService.getRepositories(user.getEncryptedGithubAccessToken());

        log.info("GitHub returned {} repositories for user {}", remote.size(), user.getId());

        Map<Long, GithubRepository> existing =
                githubRepositoryRepository.findByUser(user).stream()
                        .collect(Collectors.toMap(
                                GithubRepository::getGithubRepositoryId,
                                Function.identity()));

        List<GithubRepository> toSave = remote.stream()
                .map(dto -> apply(
                        existing.getOrDefault(dto.getId(), new GithubRepository()),
                        dto,
                        user))
                .toList();

        githubRepositoryRepository.saveAll(toSave);

        log.info("Synced {} repositories for user {}", toSave.size(), user.getId());
        return toSave.size();
    }

    private GithubRepository apply(GithubRepository entity,
                                   GithubRepositoryResponse dto,
                                   User user) {
        entity.setUser(user);
        entity.setGithubRepositoryId(dto.getId());
        entity.setName(dto.getName());
        entity.setFullName(dto.getFullName());
        entity.setDescription(dto.getDescription());
        entity.setLanguage(dto.getLanguage());
        entity.setDefaultBranch(dto.getDefaultBranch());
        entity.setPrivateRepo(dto.getPrivateRepo());
        entity.setHtmlUrl(dto.getHtmlUrl());
        entity.setCreatedAtGithub(parse(dto.getCreatedAt()));
        entity.setUpdatedAtGithub(parse(dto.getUpdatedAt()));
        entity.setSyncedAt(LocalDateTime.now());
        return entity;
    }

    private static LocalDateTime parse(String value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }
}