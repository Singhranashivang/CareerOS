package com.careeros.backend.achievement.extractor;

import com.careeros.backend.githubcommit.GithubCommit;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FeatureExtractor {

    public List<Feature> extract(List<GithubCommit> commits) {

        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (GithubCommit commit : commits) {

            String feature = classify(commit.getMessage());

            grouped
                    .computeIfAbsent(feature, k -> new ArrayList<>())
                    .add(commit.getMessage());
        }

        List<Feature> features = new ArrayList<>();

        grouped.forEach((name, evidence) ->
                features.add(
                        Feature.builder()
                                .name(name)
                                .evidence(evidence)
                                .build()
                )
        );

        return features;
    }

    private String classify(String message) {

        message = message.toLowerCase();

        // AI / LLM
        if (message.contains("ai")
                || message.contains("llm")
                || message.contains("ollama")
                || message.contains("openai")
                || message.contains("prompt")
                || message.contains("embedding")
                || message.contains("rag")
                || message.contains("achievement")) {

            return "AI Features";
        }

        // Authentication
        if (message.contains("auth")
                || message.contains("login")
                || message.contains("oauth")
                || message.contains("jwt")
                || message.contains("security")) {

            return "Authentication";
        }

        // Backend
        if (message.contains("api")
                || message.contains("endpoint")
                || message.contains("controller")
                || message.contains("service")
                || message.contains("rest")) {

            return "Backend APIs";
        }

        // Database
        if (message.contains("database")
                || message.contains("migration")
                || message.contains("flyway")
                || message.contains("sql")
                || message.contains("postgres")
                || message.contains("jpa")
                || message.contains("hibernate")) {

            return "Database";
        }

        // Frontend
        if (message.contains("react")
                || message.contains("frontend")
                || message.contains("ui")
                || message.contains("css")
                || message.contains("tailwind")) {

            return "Frontend";
        }

        // Testing
        if (message.contains("test")
                || message.contains("junit")
                || message.contains("mock")) {

            return "Testing";
        }

        // Refactoring
        if (message.contains("refactor")
                || message.contains("cleanup")
                || message.contains("clean")
                || message.contains("optimize")) {

            return "Refactoring";
        }

        // Bug fixes
        if (message.contains("fix")
                || message.contains("bug")
                || message.contains("issue")
                || message.contains("error")) {

            return "Bug Fixes";
        }

        // New feature
        if (message.contains("implement")
                || message.contains("feature")
                || message.contains("add")
                || message.contains("create")
                || message.contains("build")) {

            return "Feature Development";
        }

        // Initial repository
        if (message.contains("initial")) {
            return "Project Setup";
        }

        return "General Development";
    }
}