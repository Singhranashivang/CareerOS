package com.careeros.backend.achievement.cluster;

import com.careeros.backend.achievement.evidence.GeneratedFilePaths;
import com.careeros.backend.achievement.evidence.SourcePathHeuristics;
import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.GithubCommitFileResponse;
import com.careeros.backend.githubcommit.GithubCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Groups a repository's commits into clusters — the achievement engine's
 * unit of reasoning (one LLM call per cluster, not per repository; see
 * {@code AchievementGeneratorService}).
 *
 * Two commits join a cluster only when BOTH hold:
 *  - time proximity: within {@code windowDays} of a commit already in the
 *    chain (a rolling window, so A-B-C can span more than windowDays end to
 *    end as long as each adjacent pair is close);
 *  - path overlap: they touch the same top-level source area
 *    ({@link SourcePathHeuristics#area}), ignoring generated/vendored files.
 *
 * Implemented as union-find over commits sorted chronologically: for each
 * commit, only commits still within the window walking backward are
 * compared (sorted order means once the gap exceeds the window, nothing
 * further back can qualify either).
 *
 * A component larger than {@code maxCommits} is split into consecutive
 * chronological chunks. A component of exactly one commit survives as its
 * own cluster only if it's substantial on its own (authored lines, excluding
 * generated files, at least {@code soloMinLines}); otherwise it's dropped —
 * commits that join no cluster are never forced into one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommitClusterer {

    private final GithubApiService githubApiService;

    @Value("${app.achievement.cluster.window-days:3}")
    private int windowDays;

    @Value("${app.achievement.cluster.max-commits:8}")
    private int maxCommits;

    @Value("${app.achievement.cluster.solo-min-lines:20}")
    private int soloMinLines;

    public List<CommitCluster> cluster(GithubRepository repository,
                                        List<GithubCommit> commits,
                                        String accessToken) {

        List<GithubCommit> ordered = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .sorted(Comparator.comparing(GithubCommit::getCommittedAt))
                .toList();

        if (ordered.isEmpty()) {
            return List.of();
        }

        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];

        // One GitHub API call per commit — the same per-commit cost already
        // paid elsewhere (ChangedFilesFetcher, CodeStatsFetcher). Fetched
        // once here and carried on the cluster for evidence-building to
        // reuse, rather than fetched again per cluster later.
        Map<String, List<GithubCommitFileResponse>> filesBySha = new HashMap<>();
        Map<Integer, Set<String>> areasByIndex = new HashMap<>();
        Map<Integer, Integer> authoredLinesByIndex = new HashMap<>();

        for (int i = 0; i < ordered.size(); i++) {
            GithubCommit commit = ordered.get(i);
            List<GithubCommitFileResponse> files = githubApiService.getCommitFileStats(
                    owner, repo, commit.getGithubCommitSha(), accessToken);
            filesBySha.put(commit.getGithubCommitSha(), files);

            Set<String> areas = new HashSet<>();
            int authoredLines = 0;
            for (GithubCommitFileResponse file : files) {
                if (GeneratedFilePaths.isGenerated(file.getFilename())) continue;
                authoredLines += file.getAdditions() + file.getDeletions();
                SourcePathHeuristics.area(file.getFilename()).ifPresent(areas::add);
            }
            areasByIndex.put(i, areas);
            authoredLinesByIndex.put(i, authoredLines);
        }

        UnionFind uf = new UnionFind(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i - 1; j >= 0; j--) {
                long gapDays = Duration.between(
                        ordered.get(j).getCommittedAt(),
                        ordered.get(i).getCommittedAt()
                ).toDays();
                if (gapDays > windowDays) break; // sorted — nothing further back qualifies

                if (sharesArea(areasByIndex.get(i), areasByIndex.get(j))) {
                    uf.union(i, j);
                }
            }
        }

        Map<Integer, List<Integer>> components = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            components.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(i);
        }

        List<CommitCluster> clusters = new ArrayList<>();
        for (List<Integer> indices : components.values()) {
            if (indices.size() == 1) {
                int i = indices.get(0);
                if (authoredLinesByIndex.get(i) >= soloMinLines) {
                    clusters.add(toCluster(ordered, filesBySha, indices));
                } else {
                    log.debug("{}: dropping lone commit {} ({} authored lines, below solo-min-lines={})",
                            repository.getFullName(), ordered.get(i).getGithubCommitSha(),
                            authoredLinesByIndex.get(i), soloMinLines);
                }
                continue;
            }

            for (List<Integer> chunk : chunk(indices, maxCommits)) {
                clusters.add(toCluster(ordered, filesBySha, chunk));
            }
        }

        clusters.sort(Comparator.comparing(
                (CommitCluster c) -> c.commits().get(c.size() - 1).getCommittedAt()).reversed());

        log.info("{}: {} commits -> {} clusters ({} commits dropped)",
                repository.getFullName(), ordered.size(), clusters.size(),
                ordered.size() - clusters.stream().mapToInt(CommitCluster::size).sum());

        return clusters;
    }

    private static boolean sharesArea(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (String area : a) {
            if (b.contains(area)) return true;
        }
        return false;
    }

    private static CommitCluster toCluster(List<GithubCommit> ordered,
                                            Map<String, List<GithubCommitFileResponse>> filesBySha,
                                            List<Integer> indices) {
        List<GithubCommit> clusterCommits = indices.stream().map(ordered::get).toList();
        Map<String, List<GithubCommitFileResponse>> clusterFiles = new HashMap<>();
        for (GithubCommit c : clusterCommits) {
            clusterFiles.put(c.getGithubCommitSha(), filesBySha.get(c.getGithubCommitSha()));
        }
        return new CommitCluster(clusterCommits, clusterFiles);
    }

    /** Splits a chronologically-ordered index list into consecutive chunks of at most {@code size}. */
    private static List<List<Integer>> chunk(List<Integer> indices, int size) {
        List<List<Integer>> chunks = new ArrayList<>();
        for (int i = 0; i < indices.size(); i += size) {
            chunks.add(indices.subList(i, Math.min(i + size, indices.size())));
        }
        return chunks;
    }

    /** Plain union-find on array indices — no need for anything fancier at this scale. */
    private static final class UnionFind {
        private final int[] parent;

        UnionFind(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra != rb) parent[ra] = rb;
        }
    }
}
