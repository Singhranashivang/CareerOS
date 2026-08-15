package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.dto.GithubCommitResponse;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubCommitServiceTest {

    private static final long OWNER_GITHUB_ID = 92661186L;

    private final GithubCommitRepository commitRepository = mock(GithubCommitRepository.class);
    private final GithubCommitApiService apiService = mock(GithubCommitApiService.class);
    private final GithubCommitService service =
            new GithubCommitService(commitRepository, apiService);

    private static GithubRepository ownedRepository() {
        User owner = User.builder()
                .githubId(OWNER_GITHUB_ID)
                .username("Singhranashivang")
                .githubAccessToken("token")
                .build();

        GithubRepository repository = new GithubRepository();
        repository.setUser(owner);
        repository.setFullName("Singhranashivang/Programming_Hactoberfest25");
        return repository;
    }

    private static GithubCommitResponse commit(String sha, Long authorId, String login) {
        GithubCommitResponse.Author gitAuthor = new GithubCommitResponse.Author();
        gitAuthor.setName("Someone");
        gitAuthor.setEmail("someone@example.com");
        gitAuthor.setDate("2026-07-01T10:00:00Z");

        GithubCommitResponse.Commit inner = new GithubCommitResponse.Commit();
        inner.setMessage("Added F1 Race Predictor program for Hacktoberfest 2025");
        inner.setAuthor(gitAuthor);

        GithubCommitResponse response = new GithubCommitResponse();
        response.setSha(sha);
        response.setCommit(inner);
        response.setHtmlUrl("https://github.com/x/y/commit/" + sha);

        if (authorId != null) {
            GithubCommitResponse.AuthorAccount account =
                    new GithubCommitResponse.AuthorAccount();
            account.setId(authorId);
            account.setLogin(login);
            response.setAuthor(account);
        }
        return response;
    }

    @Test
    void persistsOnlyCommitsAuthoredByTheRepositoryOwner() {

        GithubRepository repository = ownedRepository();

        when(commitRepository.findByGithubCommitSha(anyString()))
                .thenReturn(Optional.empty());
        when(apiService.getCommits(anyString(), anyString())).thenReturn(List.of(
                commit("aaa", OWNER_GITHUB_ID, "Singhranashivang"),  // owner
                commit("bbb", 24794539L, "fineanmol"),               // someone else's PR
                commit("ccc", null, null)                            // no linked account
        ));

        service.syncCommits(repository, OWNER_GITHUB_ID, "token");

        ArgumentCaptor<GithubCommit> saved = ArgumentCaptor.forClass(GithubCommit.class);
        verify(commitRepository, times(1)).save(saved.capture());

        // Exactly one row, and it is the owner's — proving the foreign commit and
        // the unlinked commit were both dropped, and that the happy path still runs
        // (syncCommits swallows exceptions, so "nothing saved" alone proves nothing).
        assertThat(saved.getValue().getGithubCommitSha()).isEqualTo("aaa");
        assertThat(saved.getValue().getAuthorGithubId()).isEqualTo(OWNER_GITHUB_ID);
        assertThat(saved.getValue().getAuthorGithubLogin()).isEqualTo("Singhranashivang");
    }

    @Test
    void dropsForeignCommitEvenWhenItIsTheOnlyOne() {

        when(apiService.getCommits(anyString(), anyString()))
                .thenReturn(List.of(commit("bbb", 24794539L, "fineanmol")));

        int saved = service.syncCommits(ownedRepository(), OWNER_GITHUB_ID, "token");

        verify(commitRepository, times(0)).save(any());
        assertThat(saved).isZero();
    }
}
