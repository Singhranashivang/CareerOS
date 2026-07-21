package com.careeros.backend.achievement.evidence;

import com.careeros.backend.achievement.analyzer.ChangedFileAnalyzer;
import com.careeros.backend.achievement.analyzer.FileAnalyzer;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.extractor.FeatureExtractor;
import com.careeros.backend.achievement.extractor.TechnologyExtractor;
import com.careeros.backend.github.GithubRepository;
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

    public Evidence build(
            GithubRepository repository,
            List<GithubCommit> commits,
            List<GithubPullRequest> pullRequests
    ) {

        System.out.println("\n====== COMMITS RECEIVED BY EVIDENCE BUILDER ======");

        for (GithubCommit commit : commits) {
            System.out.println(commit.getMessage());
        }

        System.out.println("Commit count = " + commits.size());
        System.out.println("=============================================\n");

        List<Feature> features = featureExtractor.extract(commits);

        System.out.println("\n====== EXTRACTED FEATURES ======");

        for (Feature feature : features) {
            System.out.println(feature.getName());

            for (String evidence : feature.getEvidence()) {
                System.out.println(" - " + evidence);
            }
        }

        System.out.println("===============================\n");

        List<String> prTitles = pullRequests.stream()
                .map(GithubPullRequest::getTitle)
                .toList();

        String readme = readmeFetcher.fetch(repository);

        List<String> dependencies =
                dependencyFetcher.fetch(repository);

        List<String> repositoryTree = repositoryTreeFetcher.fetch(repository);

        List<String> repositoryFeatures =
                fileAnalyzer.analyze(repositoryTree);

        System.out.println("\n======= FILE ANALYSIS =======");

        repositoryFeatures.forEach(System.out::println);

        System.out.println("=============================\n");

        List<String> changedFiles = changedFilesFetcher.fetch(repository);

        List<String> changedFileInsights =
                changedFileAnalyzer.analyze(changedFiles);

        System.out.println("\n====== CHANGED FILE ANALYSIS ======");

        changedFileInsights.forEach(System.out::println);

        System.out.println("===================================\n");

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


        System.out.println("\n====== TECHNOLOGIES ======");

        technologies.forEach(System.out::println);

        System.out.println("==========================\n");

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
                .technologies(technologies)
                .changedFileInsights(changedFileInsights)
                .build();

    }

}