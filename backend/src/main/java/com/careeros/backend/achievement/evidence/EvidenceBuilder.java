package com.careeros.backend.achievement.evidence;

import com.careeros.backend.achievement.analyzer.ChangedFileAnalyzer;
import com.careeros.backend.achievement.analyzer.FileAnalyzer;
import com.careeros.backend.achievement.cluster.CommitCluster;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.extractor.FeatureExtractor;
import com.careeros.backend.achievement.extractor.TechnologyExtractor;
import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.GithubCommitFileResponse;
import com.careeros.backend.github.dto.GithubPullRequestSummary;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubpullrequest.GithubPullRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EvidenceBuilder {

    private final FeatureExtractor featureExtractor;
    private final ReadmeFetcher readmeFetcher;
    private final DependencyFetcher dependencyFetcher;
    private final RepositoryTreeFetcher repositoryTreeFetcher;
    private final ChangedFilesFetcher changedFilesFetcher;
    private final FileAnalyzer fileAnalyzer;
    private final ChangedFileAnalyzer changedFileAnalyzer;
    private final TechnologyExtractor technologyExtractor;
    private final CodeStatsFetcher codeStatsFetcher;
    private final GithubApiService githubApiService;

    /**
     * accessToken comes from the caller's attached User. The repository entity
     * is detached by the time it reaches here (open-in-view is off), so its
     * lazy user cannot be dereferenced.
     */
    public Evidence build(
            GithubRepository repository,
            List<GithubCommit> commits,
            List<GithubPullRequest> pullRequests,
            String accessToken
    ) {

        System.out.println("\n====== COMMITS RECEIVED BY EVIDENCE BUILDER ======");

        for (GithubCommit commit : commits) {
            System.out.println(commit.getMessage());
        }

        System.out.println("Commit count = " + commits.size());
        System.out.println("=============================================\n");

        List<Feature> features = featureExtractor.extract(commits);



        for (Feature feature : features) {
            System.out.println(feature.getName());

            for (String evidence : feature.getEvidence()) {
                System.out.println(" - " + evidence);
            }
        }


        List<String> prTitles = pullRequests.stream()
                .map(GithubPullRequest::getTitle)
                .toList();

        String readme = readmeFetcher.fetch(repository, accessToken);

        List<String> dependencies =
                dependencyFetcher.fetch(repository, accessToken);

        List<String> repositoryTree =
                repositoryTreeFetcher.fetch(repository, accessToken);

        List<String> repositoryFeatures =
                fileAnalyzer.analyze(repositoryTree);



        repositoryFeatures.forEach(System.out::println);



        List<String> changedFiles =
                changedFilesFetcher.fetch(repository, accessToken);

        List<String> changedFileInsights =
                changedFileAnalyzer.analyze(changedFiles);


        changedFileInsights.forEach(System.out::println);



        List<String> technologies = new ArrayList<>();

        if (repository.getLanguage() != null) {
            technologies.add(repository.getLanguage());
        }

        technologies.addAll(dependencies);

        technologies.addAll(
                technologyExtractor.extract(repositoryTree)
        );

        technologies.addAll(
                technologyExtractor.extract(changedFiles)
        );

        technologies = technologies.stream()
                .distinct()
                .sorted()
                .toList();



        technologies.forEach(System.out::println);


        CodeStats codeStats =
                codeStatsFetcher.fetch(repository, commits, accessToken);

        return Evidence.builder()
                .repositoryName(repository.getName())
                .description(repository.getDescription())
                .language(repository.getLanguage())
                .readme(readme)
                .dependencies(dependencies)
                .features(features)
                .repositoryFeatures(repositoryFeatures)
                .pullRequestTitles(prTitles)
                .repositoryTree(repositoryTree)
                .changedFiles(changedFiles)
                .codeStats(codeStats)
                .technologies(technologies)
                .changedFileInsights(changedFileInsights)
                .build();

    }

    /**
     * Cluster-scoped evidence for the achievement generator (see
     * AchievementGeneratorService) — one LLM call per cluster, not per
     * repository. changedFiles/deletedFiles/addedTestFiles/diffs come from
     * the cluster's own already-fetched file data (CommitCluster.filesBySha,
     * gathered once during clustering) rather than a fresh fetch; repo-level
     * context (readme, dependencies, repository tree) stays
     * repository-scoped since none of it has a meaningful "per cluster"
     * version.
     */
    public Evidence buildForCluster(
            GithubRepository repository,
            CommitCluster cluster,
            String accessToken
    ) {
        List<GithubCommit> commits = cluster.commits();

        List<Feature> features = featureExtractor.extract(commits);

        String readme = readmeFetcher.fetch(repository, accessToken);
        List<String> dependencies = dependencyFetcher.fetch(repository, accessToken);
        List<String> repositoryTree = repositoryTreeFetcher.fetch(repository, accessToken);
        List<String> repositoryFeatures = fileAnalyzer.analyze(repositoryTree);

        List<GithubCommitFileResponse> clusterFiles = cluster.filesBySha().values().stream()
                .flatMap(List::stream)
                .filter(f -> !GeneratedFilePaths.isGenerated(f.getFilename()))
                .toList();

        List<String> changedFiles = clusterFiles.stream()
                .map(GithubCommitFileResponse::getFilename)
                .distinct().sorted().toList();

        List<String> deletedFiles = clusterFiles.stream()
                .filter(f -> "removed".equals(f.getStatus()))
                .map(GithubCommitFileResponse::getFilename)
                .distinct().sorted().toList();

        List<String> addedTestFiles = clusterFiles.stream()
                .filter(f -> "added".equals(f.getStatus()) && SourcePathHeuristics.isTest(f.getFilename()))
                .map(GithubCommitFileResponse::getFilename)
                .distinct().sorted().toList();

        // See OperationalOutputLines for why log/print calls are stripped
        // here rather than left to prompt instructions alone.
        List<String> diffs = clusterFiles.stream()
                .filter(f -> f.getPatch() != null && !f.getPatch().isBlank())
                .map(f -> f.getFilename() + ":\n" + OperationalOutputLines.strip(f.getPatch()))
                .toList();

        List<String> changedFileInsights = changedFileAnalyzer.analyze(changedFiles);

        List<String> technologies = new ArrayList<>();
        if (repository.getLanguage() != null) {
            technologies.add(repository.getLanguage());
        }
        technologies.addAll(dependencies);
        technologies.addAll(technologyExtractor.extract(repositoryTree));
        technologies.addAll(technologyExtractor.extract(changedFiles));
        technologies = technologies.stream().distinct().sorted().toList();

        CodeStats codeStats = codeStatsFetcher.fetch(repository, commits, accessToken);

        GithubPullRequestSummary pullRequest = findPullRequest(repository, commits, accessToken);

        return Evidence.builder()
                .repositoryName(repository.getName())
                .description(repository.getDescription())
                .language(repository.getLanguage())
                .readme(readme)
                .dependencies(dependencies)
                .features(features)
                .repositoryFeatures(repositoryFeatures)
                .pullRequestTitles(pullRequest == null ? List.of() : List.of(pullRequest.getTitle()))
                .pullRequestTitle(pullRequest == null ? null : pullRequest.getTitle())
                .pullRequestBody(pullRequest == null ? null : pullRequest.getBody())
                .repositoryTree(repositoryTree)
                .changedFiles(changedFiles)
                .deletedFiles(deletedFiles)
                .addedTestFiles(addedTestFiles)
                .diffs(diffs)
                .codeStats(codeStats)
                .technologies(technologies)
                .changedFileInsights(changedFileInsights)
                .build();
    }

    /** Stops at the first commit that's part of a PR — one is enough to prioritise it in the prompt. */
    private GithubPullRequestSummary findPullRequest(
            GithubRepository repository, List<GithubCommit> commits, String accessToken) {

        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];

        for (GithubCommit commit : commits) {
            List<GithubPullRequestSummary> prs = githubApiService.getPullRequestsForCommit(
                    owner, repo, commit.getGithubCommitSha(), accessToken);
            if (!prs.isEmpty()) {
                return prs.get(0);
            }
        }
        return null;
    }

}