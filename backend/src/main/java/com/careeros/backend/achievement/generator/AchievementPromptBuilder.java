package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import org.springframework.stereotype.Component;

@Component
public class AchievementPromptBuilder {

    public String build(
            RepositoryKnowledge knowledge,
            Evidence evidence
    ) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are an experienced software engineer writing a resume-quality achievement based on real GitHub work.

Your writing should sound like a human engineer, not an AI assistant.

WRITING STYLE

- Be specific.
- Use strong engineering verbs like Built, Developed, Designed, Implemented, Created, Optimized, Refactored.
- Mention the actual work completed.
- Never exaggerate.
- Never invent business impact.
- Never invent technologies.
- Never invent metrics.
- Keep the wording concise and natural.

GOOD EXAMPLES

✓ Developed an F1 Race Predictor in Python for the Hacktoberfest repository and added multiple algorithm implementations to expand project functionality.

✓ Created a contributor guide and implemented several algorithmic solutions, including Merge Sort and shortest path implementations.

BAD EXAMPLES

✗ Successfully leveraged cutting-edge technologies to improve repository performance.

✗ Implemented innovative solutions that significantly enhanced system efficiency.

Generate ONE engineering achievement.

Return ONLY valid JSON.

{
  "title":"",
  "resumeBullet":"",
  "starSituation":"",
  "starTask":"",
  "starAction":"",
  "starResult":"",
  "confidence":0.95
}

=========================
Repository Knowledge
=========================

Project Type:
""");

        prompt.append(knowledge.getProjectType());

        prompt.append("\n\nDomain:\n");
        prompt.append(knowledge.getDomain());

        prompt.append("\n\nTechnologies:\n");

        for (String tech : knowledge.getTechnologies()) {
            prompt.append("- ").append(tech).append("\n");
        }

        prompt.append("\nArchitecture:\n");

        for (String arch : knowledge.getArchitecture()) {
            prompt.append("- ").append(arch).append("\n");
        }

        prompt.append("\nRepository Features:\n");

        for (String feature : knowledge.getFeatures()) {
            prompt.append("- ").append(feature).append("\n");
        }

        prompt.append("\nDeveloper Contributions:\n");

        for (String contribution : knowledge.getDeveloperContributions()) {
            prompt.append("- ").append(contribution).append("\n");
        }

        prompt.append("""

=========================
Evidence
=========================

Detected Features:
""");

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

        prompt.append("\nChanged Files:\n");

        for (String file : evidence.getChangedFiles()) {
            prompt.append("- ").append(file).append("\n");
        }

        prompt.append("\nPull Requests:\n");

        for (String pr : evidence.getPullRequestTitles()) {
            prompt.append("- ").append(pr).append("\n");
        }

        prompt.append("""

Generate one high-quality resume achievement.

Return ONLY JSON.
""");

        return prompt.toString();
    }

}