package com.careeros.backend.achievement.analyzer;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FileAnalyzer {

    public List<String> analyze(List<String> files) {

        Set<String> features = new LinkedHashSet<>();

        for (String file : files) {

            String path = file.toLowerCase();

            if (path.contains("/security/")) {
                features.add("Authentication & Security");
            }

            if (path.contains("/github/")) {
                features.add("GitHub Integration");
            }

            if (path.contains("/achievement/weekly/")) {
                features.add("Weekly Achievement Engine");
            }

            if (path.contains("/achievement/linkedin/")) {
                features.add("LinkedIn Generator");
            }

            if (path.contains("/achievement/star/")) {
                features.add("STAR Story Generator");
            }

            if (path.contains("/controller")) {
                features.add("REST APIs");
            }

            if (path.contains("/config/")) {
                features.add("Application Configuration");
            }

            if (path.contains("/repository")) {
                features.add("Database Layer");
            }

            if (path.contains("/service")) {
                features.add("Business Logic");
            }

            if (path.contains("/llm/")) {
                features.add("AI Integration");
            }

            if (path.contains("pom.xml")) {
                features.add("Maven Project");
            }

            if (path.contains("docker")) {
                features.add("Docker");
            }

            if (path.contains("flyway")) {
                features.add("Database Migration");
            }
        }

        return new ArrayList<>(features);
    }

}