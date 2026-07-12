package com.careeros.backend.achievement.weekly;

import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubpullrequest.GithubPullRequest;
import org.springframework.stereotype.Component;

@Component
public class WeeklyPromptBuilder {

    public String buildPrompt(WeeklyAchievementInput input) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are an expert engineering manager.

Summarize this week's software engineering work.

Generate ONE professional weekly achievement.

Repositories:
""");

        input.getRepositories().forEach(repo ->
                prompt.append("- ")
                        .append(repo.getName())
                        .append("\n"));

        prompt.append("\nCommits:\n");

        for (GithubCommit commit : input.getCommits()) {

            prompt.append("- ")
                    .append(commit.getMessage())
                    .append("\n");
        }

        prompt.append("\nPull Requests:\n");

        for (GithubPullRequest pr : input.getPullRequests()) {

            prompt.append("- ")
                    .append(pr.getTitle())
                    .append("\n");
        }

        prompt.append("""

Return ONLY JSON.

{
"title":"",
"description":"",
"confidence":0.95
}
""");

        return prompt.toString();

    }

}