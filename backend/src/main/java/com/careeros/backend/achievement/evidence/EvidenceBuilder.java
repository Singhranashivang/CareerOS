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
    private final CodeStatsFetcher codeStatsFetcher;

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

}