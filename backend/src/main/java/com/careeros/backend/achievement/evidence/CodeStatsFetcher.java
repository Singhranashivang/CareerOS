package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.GithubCommitFileResponse;
import com.careeros.backend.githubcommit.GithubCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeStatsFetcher {

    /**
     * Each commit costs one GitHub API call, against a 5,000/hour limit.
     * 40 recent commits is enough to characterise the work without spending
     * the budget for the rest of the user's repositories.
     */
    private static final int MAX_COMMITS = 40;

    private final GithubApiService githubApiService;

    /** Token passed in; the repository is detached and its user is lazy. */
    public CodeStats fetch(GithubRepository repository,
                           List<GithubCommit> commits,
                           String accessToken) {

        if (commits == null || commits.isEmpty()) {
            return CodeStats.builder().commitCount(0).areasTouched(List.of()).build();
        }

        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];
        String token = accessToken;

        List<GithubCommit> ordered = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .sorted(Comparator.comparing(GithubCommit::getCommittedAt))
                .toList();

        // Every commit could have a null date; the span calculation below indexes
        // into this list unconditionally.
        if (ordered.isEmpty()) {
            return CodeStats.builder()
                    .commitCount(0)
                    .areasTouched(List.of())
                    .build();
        }

        List<GithubCommit> sampled = ordered.size() <= MAX_COMMITS
                ? ordered
                : ordered.subList(ordered.size() - MAX_COMMITS, ordered.size());

        int added = 0, deleted = 0, testFiles = 0, newFiles = 0, skipped = 0;
        Set<String> files = new HashSet<>();
        Set<String> areas = new HashSet<>();

        for (GithubCommit commit : sampled) {
            for (GithubCommitFileResponse file : githubApiService.getCommitFileStats(
                    owner, repo, commit.getGithubCommitSha(), token)) {

                // A vendored tree or a lockfile is churn, not work. Counting it
                // let a 35-line project measure as 1,265 lines.
                if (GeneratedFilePaths.isGenerated(file.getFilename())) {
                    skipped++;
                    continue;
                }

                added += file.getAdditions();
                deleted += file.getDeletions();
                files.add(file.getFilename());

                if (isTest(file.getFilename())) testFiles++;
                if ("added".equals(file.getStatus())) newFiles++;

                area(file.getFilename()).ifPresent(areas::add);
            }
        }

        Duration span = Duration.between(
                ordered.get(0).getCommittedAt(),
                ordered.get(ordered.size() - 1).getCommittedAt()
        );

        CodeStats stats = CodeStats.builder()
                .commitCount(ordered.size())
                .sampledCommits(sampled.size())
                .generatedFilesSkipped(skipped)
                .filesTouched(files.size())
                .linesAdded(added)
                .linesDeleted(deleted)
                .testFilesTouched(testFiles)
                .newFilesCreated(newFiles)
                .areasTouched(areas.stream().sorted().limit(8).toList())
                .spanDays(span.toDays())
                .spanMinutes(span.toMinutes())
                .firstCommit(ordered.get(0).getCommittedAt().toLocalDate().toString())
                .lastCommit(ordered.get(ordered.size() - 1).getCommittedAt().toLocalDate().toString())
                .build();

        log.info("{}: {} commits ({} sampled), +{}/-{} authored lines across {} files "
                        + "over {} days, {} generated files skipped",
                repository.getName(), stats.getCommitCount(), stats.getSampledCommits(),
                stats.getLinesAdded(), stats.getLinesDeleted(),
                stats.getFilesTouched(), stats.getSpanDays(),
                stats.getGeneratedFilesSkipped());

        return stats;
    }

    private static boolean isTest(String path) {
        String p = path.toLowerCase();
        return p.contains("/test/") || p.contains("__tests__")
                || p.endsWith(".test.ts") || p.endsWith(".test.tsx")
                || p.endsWith(".spec.ts") || p.endsWith("test.java")
                || p.endsWith("_test.py") || p.endsWith("test_.py");
    }

    /** "src/main/java/com/x/security/Foo.java" → "security" */
    private static Optional<String> area(String path) {
        String[] segments = path.split("/");
        if (segments.length < 2) return Optional.empty();

        Set<String> skip = Set.of("src", "main", "java", "com", "org", "app",
                "lib", "test", "resources", "node_modules", "target", "build");

        for (int i = segments.length - 2; i >= 0; i--) {
            String s = segments[i];
            if (!skip.contains(s) && s.length() > 2 && !s.contains(".")) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}