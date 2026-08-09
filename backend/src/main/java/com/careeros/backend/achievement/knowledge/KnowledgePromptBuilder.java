package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePromptBuilder {

    public String build(Evidence evidence) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are a Staff Software Engineer reviewing a GitHub repository.

Your job is to understand the repository exactly as an experienced engineer would after reading the codebase.

Analyze ONLY the evidence provided.

WRITING RULES

- Base every conclusion on the provided evidence.
- Do not guess.
- If information is missing, leave the field empty instead of inventing it.
- Keep feature names concise.
- Prefer concrete technologies over generic terms.
- Identify architecture only when there is clear evidence.
- Summarize developer contributions from the commits and changed files.

DO NOT

- Invent technologies.
- Invent frameworks.
- Invent repository features.
- Invent architecture.
- Invent business goals.
- Invent developer work.

GOOD EXAMPLES

Project Type:
Algorithm Repository

Technologies:
Java
Python
C++

Developer Contributions:
- Added F1 Race Predictor
- Implemented Merge Sort
- Created CONTRIBUTING guide
- Started shortest path testing

OUTPUT SCHEMA

Return ONLY valid JSON, matching this shape exactly:

{
  "repositoryName": string,
  "projectType":    string,
  "domain":         string,
  "technologies":            [string, string, ...],
  "architecture":            [string, string, ...],
  "features":                [string, string, ...],
  "developerContributions":  [string, string, ...],
  "confidence":     number between 0 and 1
}

SHAPE RULES — these are not optional

- The four list fields are FLAT ARRAYS OF STRINGS. Nothing else.
- No field may be an object. If the schema above does not show { }, do not emit { }.
- Do not nest, group, or label the array entries.
- An empty array is a valid answer. A wrong shape is not.

Correct:

  "architecture": ["Controller layer", "Service layer", "Repository layer"]

Wrong — do not do this:

  "architecture": {"type": "Layered Architecture", "layers": ["Controller", "Service"]}
  "architecture": "Layered Architecture"

==========================
REPOSITORY
==========================

Repository Name:
""");

        prompt.append(evidence.getRepositoryName());

        prompt.append("\n\nDescription:\n");
        prompt.append(evidence.getDescription());

        prompt.append("\n\nPrimary Language:\n");
        prompt.append(evidence.getLanguage());

        prompt.append("\n\nREADME:\n");
        prompt.append(
                evidence.getReadme() == null || evidence.getReadme().isBlank()
                        ? "README not available."
                        : evidence.getReadme()
        );

        prompt.append("\n\nDependencies:\n");

        for (String dependency : evidence.getDependencies()) {

            prompt.append("- ")
                    .append(dependency)
                    .append("\n");

        }

        prompt.append("\n\nRepository Tree:\n");

        for (String file : evidence.getRepositoryTree()) {

            prompt.append("- ")
                    .append(file)
                    .append("\n");

        }

        prompt.append("\n\nChanged Files:\n");

        for (String file : evidence.getChangedFiles()) {

            prompt.append("- ")
                    .append(file)
                    .append("\n");

        }

        prompt.append("\n\nDetected Features:\n");

        for (Feature feature : evidence.getFeatures()) {

            prompt.append("\nFeature: ")
                    .append(feature.getName())
                    .append("\n");

            for (String item : feature.getEvidence()) {

                prompt.append(" - ")
                        .append(item)
                        .append("\n");

            }

        }

        prompt.append("\n\nPull Requests:\n");

        for (String pr : evidence.getPullRequestTitles()) {

            prompt.append("- ")
                    .append(pr)
                    .append("\n");

        }

        prompt.append("""

==========================

Using ONLY the evidence above:

1. Identify the project type.
2. Identify the business domain.
3. Identify the technologies actually used.
4. Infer the architecture where supported.
5. Summarize the major features.
6. Describe the developer's contributions.

Return ONLY valid JSON.
""");

        return prompt.toString();
    }

}