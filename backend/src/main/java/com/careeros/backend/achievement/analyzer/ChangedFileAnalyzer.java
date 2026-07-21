package com.careeros.backend.achievement.analyzer;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ChangedFileAnalyzer {

    public List<String> analyze(List<String> changedFiles) {

        Set<String> insights = new LinkedHashSet<>();

        for (String file : changedFiles) {

            String path = file.toLowerCase();

            if (path.contains("controller")) {
                insights.add("REST API Development");
            }

            if (path.contains("service")) {
                insights.add("Business Logic");
            }

            if (path.contains("repository")) {
                insights.add("Database Layer");
            }

            if (path.contains("security")) {
                insights.add("Authentication & Security");
            }

            if (path.contains("config")) {
                insights.add("Application Configuration");
            }

            if (path.contains("github")) {
                insights.add("GitHub Integration");
            }

            if (path.contains("achievement")) {
                insights.add("Achievement Engine");
            }

            if (path.contains("weekly")) {
                insights.add("Weekly Summary Engine");
            }

            if (path.contains("star")) {
                insights.add("STAR Story Generator");
            }

            if (path.contains("linkedin")) {
                insights.add("LinkedIn Generator");
            }

            if (path.contains("llm")) {
                insights.add("LLM Integration");
            }

            if (path.endsWith(".sql")) {
                insights.add("Database Migration");
            }

            if (path.endsWith(".yml") || path.endsWith(".yaml")) {
                insights.add("Configuration");
            }

            if (path.endsWith(".md")) {
                insights.add("Documentation");
            }
        }

        return new ArrayList<>(insights);
    }
}