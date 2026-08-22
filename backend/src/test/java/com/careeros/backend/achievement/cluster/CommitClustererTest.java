package com.careeros.backend.achievement.cluster;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.GithubCommitFileResponse;
import com.careeros.backend.githubcommit.GithubCommit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CommitClusterer's grouping rule (time proximity AND path overlap, rolling
 * window, 8-commit cap, solo-substantial exception, drop-unclustered) is
 * real branching logic with no other test — CommitClustererRealDataReport is
 * a throwaway real-API verification, not a regression test. This exercises
 * the algorithm against synthetic commits/files, no network calls.
 */
class CommitClustererTest {

    private static final GithubRepository REPO = GithubRepository.builder()
            .id(1L).name("repo").fullName("owner/repo").build();

    private final GithubApiService githubApiService = mock(GithubApiService.class);
    private final CommitClusterer clusterer = new CommitClusterer(githubApiService);

    CommitClustererTest() {
        ReflectionTestUtils.setField(clusterer, "windowDays", 3);
        ReflectionTestUtils.setField(clusterer, "maxCommits", 8);
        ReflectionTestUtils.setField(clusterer, "soloMinLines", 20);
    }

    private static GithubCommit commit(String sha, LocalDateTime at) {
        return GithubCommit.builder().githubCommitSha(sha).committedAt(at).message("work").build();
    }

    /** additions=lines so authoredLines is exactly what the test asks for. */
    private void filesFor(String sha, int lines, String... paths) {
        List<GithubCommitFileResponse> files = List.of(paths).stream().map(path -> {
            GithubCommitFileResponse f = new GithubCommitFileResponse();
            f.setFilename(path);
            f.setAdditions(lines);
            f.setStatus("modified");
            return f;
        }).toList();
        when(githubApiService.getCommitFileStats(eq("owner"), eq("repo"), eq(sha), any()))
                .thenReturn(files);
    }

    @Test
    void sameDaySameAreaMergesIntoOneCluster() {
        LocalDateTime day = LocalDateTime.of(2026, 8, 1, 10, 0);
        GithubCommit a = commit("a", day);
        GithubCommit b = commit("b", day.plusHours(2));
        filesFor("a", 15, "src/main/java/com/x/service/Foo.java");
        filesFor("b", 15, "src/main/java/com/x/service/Bar.java");

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a, b), "token");

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).commits()).containsExactly(a, b);
    }

    @Test
    void sameDayDifferentAreaDoesNotMerge() {
        LocalDateTime day = LocalDateTime.of(2026, 8, 1, 10, 0);
        GithubCommit a = commit("a", day);
        GithubCommit b = commit("b", day.plusHours(2));
        filesFor("a", 30, "src/main/java/com/x/service/Foo.java");
        filesFor("b", 30, "src/main/java/com/x/billing/Bar.java");

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a, b), "token");

        assertThat(clusters).hasSize(2);
        assertThat(clusters).allMatch(c -> c.size() == 1);
    }

    @Test
    void sameAreaButOutsideWindowDoesNotMerge() {
        LocalDateTime day = LocalDateTime.of(2026, 8, 1, 10, 0);
        GithubCommit a = commit("a", day);
        GithubCommit b = commit("b", day.plusDays(4)); // window is 3 days
        filesFor("a", 30, "src/main/java/com/x/service/Foo.java");
        filesFor("b", 30, "src/main/java/com/x/service/Bar.java");

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a, b), "token");

        assertThat(clusters).hasSize(2);
    }

    @Test
    void rollingWindowChainsThroughAnIntermediateCommit() {
        // a-b close, b-c close, a-c NOT close on their own (5 days apart) —
        // still one cluster, because the window rolls through b.
        LocalDateTime day = LocalDateTime.of(2026, 8, 1, 10, 0);
        GithubCommit a = commit("a", day);
        GithubCommit b = commit("b", plusDays(day, 2, 12));
        GithubCommit c = commit("c", day.plusDays(5));
        filesFor("a", 10, "src/main/java/com/x/service/Foo.java");
        filesFor("b", 10, "src/main/java/com/x/service/Bar.java");
        filesFor("c", 10, "src/main/java/com/x/service/Baz.java");

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a, b, c), "token");

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).commits()).containsExactly(a, b, c);
    }

    @Test
    void aSubstantialLoneCommitSurvivesAsItsOwnCluster() {
        GithubCommit a = commit("a", LocalDateTime.of(2026, 8, 1, 10, 0));
        filesFor("a", 25, "src/main/java/com/x/service/Foo.java"); // >= soloMinLines (20)

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a), "token");

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).commits()).containsExactly(a);
    }

    @Test
    void aTrivialLoneCommitIsDropped() {
        GithubCommit a = commit("a", LocalDateTime.of(2026, 8, 1, 10, 0));
        filesFor("a", 5, "src/main/java/com/x/service/Foo.java"); // below soloMinLines (20)

        List<CommitCluster> clusters = clusterer.cluster(REPO, List.of(a), "token");

        assertThat(clusters).isEmpty();
    }

    @Test
    void aComponentLargerThanTheCapSplitsIntoChronologicalChunks() {
        LocalDateTime day = LocalDateTime.of(2026, 8, 1, 10, 0);
        List<GithubCommit> commits = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String sha = "c" + i;
            GithubCommit commit = commit(sha, day.plusHours(i));
            commits.add(commit);
            filesFor(sha, 10, "src/main/java/com/x/service/File" + i + ".java");
        }

        List<CommitCluster> clusters = clusterer.cluster(REPO, commits, "token");

        assertThat(clusters).hasSize(2);
        assertThat(clusters.stream().mapToInt(CommitCluster::size).sum()).isEqualTo(9);
        assertThat(clusters).allMatch(c -> c.size() <= 8);
    }

    private static LocalDateTime plusDays(LocalDateTime base, int days, int hours) {
        return base.plusDays(days).plusHours(hours);
    }
}
