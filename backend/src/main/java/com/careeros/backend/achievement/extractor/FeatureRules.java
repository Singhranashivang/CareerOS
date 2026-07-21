package com.careeros.backend.achievement.extractor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FeatureRules {

    public List<FeatureRule> getRules() {

        return List.of(

                FeatureRule.builder()
                        .feature("GitHub OAuth")
                        .keywords(List.of(
                                "oauth",
                                "login",
                                "callback",
                                "jwt",
                                "token"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Repository Synchronization")
                        .keywords(List.of(
                                "repository",
                                "repositories",
                                "sync"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Commit Synchronization")
                        .keywords(List.of(
                                "commit",
                                "commits"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Pull Request Synchronization")
                        .keywords(List.of(
                                "pull",
                                "pr"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Achievement Engine")
                        .keywords(List.of(
                                "achievement",
                                "prompt"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("AI Integration")
                        .keywords(List.of(
                                "ollama",
                                "llm",
                                "gpt",
                                "ai"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Database")
                        .keywords(List.of(
                                "postgres",
                                "sql",
                                "migration",
                                "flyway"
                        ))
                        .build(),

                FeatureRule.builder()
                        .feature("Docker")
                        .keywords(List.of(
                                "docker",
                                "compose"
                        ))
                        .build()

        );
    }
}