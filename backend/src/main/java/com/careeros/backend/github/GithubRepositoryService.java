package com.careeros.backend.github;

import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.dto.GithubRepositoryResponse;
import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.github.dto.RepositoryResponse;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgeRepository;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubRepositoryService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiService githubApiService;
    private final GithubCommitRepository githubCommitRepository;
    private final AchievementRepository achievementRepository;
    private final GithubTokenEncryptor githubTokenEncryptor;

    @Transactional(readOnly = true)
    public List<RepositoryResponse> listForUser(User user) {

        var repositories =
                githubRepositoryRepository.findByUserOrderByUpdatedAtGithubDesc(user);

        Map<Long, Long> commits =
                toMap(githubCommitRepository.countPerRepository(user));
        Map<Long, Long> achievements =
                toMap(achievementRepository.countPerRepository(user));

        // The analysis outcome now lives on the repository row. It used to be
        // inferred from a repository_knowledge row existing, which could not
        // represent "analysed, nothing to claim".
        return repositories.stream()
                .map(r -> RepositoryResponse.from(
                        r,
                        achievements.getOrDefault(r.getId(), 0L).intValue(),
                        commits.getOrDefault(r.getId(), 0L).intValue()))
                .toList();
    }

    private static Map<Long, Long> toMap(List<RepositoryCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                RepositoryCountProjection::getRepositoryId,
                RepositoryCountProjection::getTotal));
    }

    @Transactional(readOnly = true)
    public GithubRepository requireOwned(User user, Long repositoryId) {
        return githubRepositoryRepository.findByIdAndUser(repositoryId, user)
                .orElseThrow(() -> new AccessDeniedException("Repository not found"));
    }

    @Transactional
    public int syncRepositories(User user) {

        var remote = githubApiService.getRepositories(githubTokenEncryptor.decrypt(user.getGithubAccessToken()));

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