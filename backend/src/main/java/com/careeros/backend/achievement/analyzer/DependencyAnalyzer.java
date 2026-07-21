package com.careeros.backend.achievement.analyzer;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DependencyAnalyzer {

    private static final Pattern ARTIFACT_PATTERN =
            Pattern.compile("<artifactId>(.*?)</artifactId>");

    public List<String> analyze(String pomContent) {

        if (pomContent == null || pomContent.isBlank()) {
            return List.of();
        }

        Set<String> technologies = new LinkedHashSet<>();

        Matcher matcher = ARTIFACT_PATTERN.matcher(pomContent);

        while (matcher.find()) {

            String artifact = matcher.group(1).toLowerCase();

            switch (artifact) {

                case "spring-boot-starter-web" ->
                        technologies.add("Spring Boot");

                case "spring-boot-starter-security" ->
                        technologies.add("Spring Security");

                case "spring-boot-starter-data-jpa" ->
                        technologies.add("Spring Data JPA");

                case "spring-boot-starter-oauth2-client" ->
                        technologies.add("OAuth2");

                case "spring-boot-starter-validation" ->
                        technologies.add("Bean Validation");

                case "postgresql" ->
                        technologies.add("PostgreSQL");

                case "flyway-core" ->
                        technologies.add("Flyway");

                case "jackson-databind" ->
                        technologies.add("Jackson");

                case "lombok" ->
                        technologies.add("Lombok");

                case "openai-java" ->
                        technologies.add("OpenAI");

                case "ollama-java" ->
                        technologies.add("Ollama");

                case "jjwt-api" ->
                        technologies.add("JWT");

                default -> {
                }
            }
        }

        return new ArrayList<>(technologies);
    }
}