package com.careeros.backend.achievement.llm;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildPrompt(Evidence evidence) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are an experienced software engineer writing a resume-quality engineering achievement based on real GitHub activity.

Your job is to transform raw repository evidence into ONE clear, accurate, and concise achievement.

WRITING STYLE

- Sound like a real engineer, not an AI assistant.
- Be specific.
- Use strong engineering verbs such as Built, Developed, Designed, Implemented, Created, Refactored, Optimized, Automated, Integrated, Fixed.
- Mention only work supported by the evidence.
- Keep the description concise (40–80 words).
- Avoid buzzwords and marketing language.

DO NOT

- Invent features.
- Invent technologies.
- Invent business impact.
- Invent performance improvements.
- Invent metrics.
- Assume work that is not present in the evidence.

GOOD EXAMPLES

✓ Built an F1 Race Predictor in Python and expanded the repository with multiple algorithm implementations, including Merge Sort and Maximum Product of Two Elements. Also improved contributor documentation.

✓ Developed several algorithm solutions and enhanced repository documentation while contributing to the Hacktoberfest project.

BAD EXAMPLES

✗ Leveraged cutting-edge technologies to significantly improve system efficiency.

✗ Successfully implemented innovative solutions that transformed repository performance.

Repository:
""");

        prompt.append(evidence.getRepositoryName())
                .append("\n");

        prompt.append("\n\nTechnologies:\n");

        for (String tech : evidence.getTechnologies()) {

            prompt.append("- ")
                    .append(tech)
                    .append("\n");

        }

        prompt.append("\nFeatures:\n");

        for (Feature feature : evidence.getFeatures()) {

            prompt.append("\n")
                    .append(feature.getName())
                    .append("\n");

            for (String item : feature.getEvidence()) {

                prompt.append(" - ")
                        .append(item)
                        .append("\n");

            }

        }

        prompt.append("\nPull Requests:\n");

        for (String pr : evidence.getPullRequestTitles()) {

            prompt.append("- ")
                    .append(pr)
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