package com.careeros.backend.achievement.llm;

import com.careeros.backend.achievement.engine.AchievementInput;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubpullrequest.GithubPullRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildPrompt(AchievementInput input) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are an expert software engineering career coach.

Based on the following GitHub activity, generate ONE professional achievement.

Repository:
""");

        prompt.append(input.getRepository().getName());

        prompt.append("\n\nCommits:\n");

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

Return ONLY valid JSON.

{
"title":"",
"description":"",
"confidence":0.95
}
""");

        return prompt.toString();
    }
}