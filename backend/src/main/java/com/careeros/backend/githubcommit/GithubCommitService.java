package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.dto.GithubCommitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubCommitService {

    /** Only the first few commits are dumped; the rest would be noise. */
    private static final int DEBUG_SAMPLE = 5;

    private final GithubCommitRepository githubCommitRepository;
    private final GithubCommitApiService githubCommitApiService;

    /**
     * ownerGithubId is passed in rather than read from repository.getUser().
     * The repository arrives detached from the controller (open-in-view is off),
     * so dereferencing its lazy user threw LazyInitializationException — which
     * the catch below swallowed, leaving every sync silently writing nothing.
     *
     * @return how many commits were saved
     */
    @Transactional
    public int syncCommits(
            GithubRepository repository,
            Long ownerGithubId,
            String accessToken
    ) {

        int saved = 0;

        try {

            var commits = githubCommitApiService.getCommits(
                    accessToken,
                    repository.getFullName()
            );

            log.info("Syncing {} — {} commits returned by GitHub",
                    repository.getFullName(), commits.size());

            log.debug("Owner github id = {} ({})",
                    ownerGithubId,
                    ownerGithubId == null ? "null" : ownerGithubId.getClass().getName());

            int index = 0;

            for (GithubCommitResponse commit : commits) {

                if (index++ < DEBUG_SAMPLE) {
                    logAuthorDiagnostics(commit, ownerGithubId);
                }

                if (!authoredBy(commit, ownerGithubId)) {
                    continue;
                }

                if (githubCommitRepository
                        .findByGithubCommitSha(commit.getSha())
                        .isPresent()) {
                    continue;
                }

                GithubCommit entity = GithubCommit.builder()
                        .repository(repository)
                        .githubCommitSha(commit.getSha())
                        .message(commit.getCommit().getMessage())
                        .authorName(commit.getCommit().getAuthor().getName())
                        .authorEmail(commit.getCommit().getAuthor().getEmail())
                        .authorGithubId(commit.getAuthor().getId())
                        .authorGithubLogin(commit.getAuthor().getLogin())
                        .committedAt(
                                LocalDateTime.parse(
                                        commit.getCommit().getAuthor().getDate()
                                                .replace("Z", "")
                                )
                        )
                        .htmlUrl(commit.getHtmlUrl())
                        .syncedAt(LocalDateTime.now())
                        .build();

                githubCommitRepository.save(entity);
                saved++;
            }

            log.info("Saved {} commits for {}", saved, repository.getFullName());

        } catch (Exception e) {

            // Previously swallowed, which is why the caller still reported
            // success while nothing was written.
            log.error("Commit sync failed for {}", repository.getFullName(), e);

        }

        return saved;
    }

    private void logAuthorDiagnostics(GithubCommitResponse commit, Long ownerGithubId) {

        var author = commit.getAuthor();

        if (author == null) {
            log.debug("commit {} — author object is NULL (deserialized as no linked account)",
                    commit.getSha());
            return;
        }

        Long authorId = author.getId();

        log.debug("commit {} — author.login={} author.id={} ({}) | owner={} ({}) | equals={}",
                commit.getSha(),
                author.getLogin(),
                authorId,
                authorId == null ? "null" : authorId.getClass().getName(),
                ownerGithubId,
                ownerGithubId == null ? "null" : ownerGithubId.getClass().getName(),
                authorId != null && authorId.equals(ownerGithubId));
    }

    /**
     * Only the repository owner's own commits are evidence of their work. A
     * Hacktoberfest repo is mostly other people's pull requests, and storing
     * those made the achievement engine credit the owner for them.
     *
     * Matched on the GitHub account id, never on commit.author.email — that is
     * a git config value the committer chooses freely. A null author means
     * GitHub could not link the commit to any account, so ownership cannot be
     * established and the commit is dropped.
     */
    private static boolean authoredBy(GithubCommitResponse commit, Long ownerGithubId) {
        return commit.getAuthor() != null
                && commit.getAuthor().getId() != null
                && commit.getAuthor().getId().equals(ownerGithubId);
    }
}
