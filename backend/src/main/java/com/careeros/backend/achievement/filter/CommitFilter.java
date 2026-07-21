package com.careeros.backend.achievement.filter;

import com.careeros.backend.githubcommit.GithubCommit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommitFilter {

    public List<GithubCommit> filter(List<GithubCommit> commits) {

        return commits.stream()

                .filter(commit -> {

                    String message = commit.getMessage().toLowerCase();

                    return !(message.contains("readme")
                            || message.contains("typo")
                            || message.contains("spacing")
                            || message.contains("merge pull request")
                            || message.startsWith("merge")
                            || message.contains("format")
                            || message.contains("comment")
                            || message.contains("bump version"));

                })

                .toList();
    }
}