package com.careeros.backend.achievement.recommendation;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositoryRecommendationService {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final RepositoryScoreCalculator repositoryScoreCalculator;

    public List<RepositoryRecommendation> recommend(User user) {

        List<GithubRepository> repositories =
                githubRepositoryRepository.findByUser(user);

        List<RepositoryRecommendation> recommendations =
                new ArrayList<>();

        for (GithubRepository repository : repositories) {

            int commitCount =
                    githubCommitRepository.findByRepository(repository).size();

            int prCount =
                    githubPullRequestRepository.findByRepository(repository).size();

            // We'll improve these later
            int technologyCount = 0;
            int featureCount = 0;

            boolean hasReadme = true;
            boolean hasDescription =
                    repository.getDescription() != null &&
                            !repository.getDescription().isBlank();

            int score = repositoryScoreCalculator.calculate(
                    commitCount,
                    prCount,
                    technologyCount,
                    featureCount,
                    hasReadme,
                    hasDescription
            );

            List<String> reasons = new ArrayList<>();

            if (commitCount > 0) {
                reasons.add(commitCount + " commits");
            }

            if (prCount > 0) {
                reasons.add(prCount + " pull requests");
            }

            if (hasDescription) {
                reasons.add("Repository has a description");
            }

            if (repository.getLanguage() != null) {
                reasons.add("Primary language: " + repository.getLanguage());
            }

            recommendations.add(
                    RepositoryRecommendation.builder()
                            .repository(repository)
                            .score(score)
                            .reasons(reasons)
                            .build()
            );
        }

        recommendations.sort(
                Comparator.comparingInt(RepositoryRecommendation::getScore)
                        .reversed()
        );

        return recommendations;
    }
}