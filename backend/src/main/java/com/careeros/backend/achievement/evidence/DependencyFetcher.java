package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DependencyFetcher {

    private final GithubApiService githubApiService;

    public List<String> fetch(GithubRepository repository) {

        String[] parts = repository.getFullName().split("/");

        String owner = parts[0];
        String repo = parts[1];
        String token = repository.getUser().getEncryptedGithubAccessToken();

        List<String> dependencies = new ArrayList<>();

        extractPomDependencies(owner, repo, token, dependencies);
        extractPackageJsonDependencies(owner, repo, token, dependencies);
        extractRequirementsDependencies(owner, repo, token, dependencies);
        extractCargoDependencies(owner, repo, token, dependencies);

        return dependencies.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private void extractPomDependencies(
            String owner,
            String repo,
            String token,
            List<String> dependencies
    ) {

        String pom = githubApiService.getFile(
                owner,
                repo,
                "pom.xml",
                token
        );

        if (pom.isBlank()) {
            return;
        }

        addIfContains(pom, "spring-boot", "Spring Boot", dependencies);
        addIfContains(pom, "spring-security", "Spring Security", dependencies);
        addIfContains(pom, "spring-data", "Spring Data JPA", dependencies);
        addIfContains(pom, "postgresql", "PostgreSQL", dependencies);
        addIfContains(pom, "mysql", "MySQL", dependencies);
        addIfContains(pom, "flyway", "Flyway", dependencies);
        addIfContains(pom, "lombok", "Lombok", dependencies);
        addIfContains(pom, "jjwt", "JWT", dependencies);
        addIfContains(pom, "kafka", "Kafka", dependencies);
        addIfContains(pom, "redis", "Redis", dependencies);
        addIfContains(pom, "openai", "OpenAI", dependencies);
        addIfContains(pom, "ollama", "Ollama", dependencies);
    }

    private void extractPackageJsonDependencies(
            String owner,
            String repo,
            String token,
            List<String> dependencies
    ) {

        String packageJson = githubApiService.getFile(
                owner,
                repo,
                "package.json",
                token
        );

        if (packageJson.isBlank()) {
            return;
        }

        addIfContains(packageJson, "\"react\"", "React", dependencies);
        addIfContains(packageJson, "\"next\"", "Next.js", dependencies);
        addIfContains(packageJson, "\"express\"", "Express", dependencies);
        addIfContains(packageJson, "\"typescript\"", "TypeScript", dependencies);
        addIfContains(packageJson, "\"vite\"", "Vite", dependencies);
        addIfContains(packageJson, "\"tailwindcss\"", "Tailwind CSS", dependencies);
        addIfContains(packageJson, "\"mongodb\"", "MongoDB", dependencies);
        addIfContains(packageJson, "\"mongoose\"", "Mongoose", dependencies);
    }

    private void extractRequirementsDependencies(
            String owner,
            String repo,
            String token,
            List<String> dependencies
    ) {

        String requirements = githubApiService.getFile(
                owner,
                repo,
                "requirements.txt",
                token
        );

        if (requirements.isBlank()) {
            return;
        }

        addIfContains(requirements, "django", "Django", dependencies);
        addIfContains(requirements, "flask", "Flask", dependencies);
        addIfContains(requirements, "fastapi", "FastAPI", dependencies);
        addIfContains(requirements, "tensorflow", "TensorFlow", dependencies);
        addIfContains(requirements, "torch", "PyTorch", dependencies);
    }

    private void extractCargoDependencies(
            String owner,
            String repo,
            String token,
            List<String> dependencies
    ) {

        String cargo = githubApiService.getFile(
                owner,
                repo,
                "Cargo.toml",
                token
        );

        if (cargo.isBlank()) {
            return;
        }

        addIfContains(cargo, "tokio", "Tokio", dependencies);
        addIfContains(cargo, "serde", "Serde", dependencies);
        addIfContains(cargo, "actix-web", "Actix Web", dependencies);
    }

    private void addIfContains(
            String content,
            String keyword,
            String technology,
            List<String> dependencies
    ) {

        if (content.toLowerCase().contains(keyword.toLowerCase())) {
            dependencies.add(technology);
        }

    }

}