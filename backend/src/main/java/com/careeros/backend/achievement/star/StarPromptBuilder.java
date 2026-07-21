package com.careeros.backend.achievement.star;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.springframework.stereotype.Component;

@Component
public class StarPromptBuilder {

    public String build(Evidence evidence) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are an experienced software engineer preparing for a technical interview.

Your task is to convert real GitHub activity into ONE authentic STAR interview story.

The story should sound like a candidate answering:
"Tell me about a project you worked on."

WRITING STYLE

- Write naturally.
- Be concise.
- Sound like a real engineer explaining their work.
- Focus on engineering decisions and implementation.
- Do not sound like an AI assistant.
- Do not sound like a performance review.

CRITICAL RULES

- Use ONLY the evidence provided.
- Never invent repositories.
- Never invent technologies.
- Never invent companies.
- Never invent metrics.
- Never invent business impact.
- Never invent measurable results.

- If there is no measurable outcome, clearly state:
  "No measurable result was available from the GitHub evidence."

- If evidence is missing for any section, write:
  "Not enough evidence available from GitHub activity."

Return ONLY valid JSON.

{
  "title":"",
  "situation":"",
  "task":"",
  "action":"",
  "result":"",
  "confidence":0.95
}

========================
EVIDENCE
========================

Repository:
""");

        prompt.append(evidence.getRepositoryName());

        prompt.append("\n\nTechnologies:\n");

        if (evidence.getTechnologies().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String tech : evidence.getTechnologies()) {
                prompt.append("- ").append(tech).append("\n");
            }
        }

        prompt.append("\nRepository Analysis:\n");

        if (evidence.getRepositoryFeatures().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String feature : evidence.getRepositoryFeatures()) {
                prompt.append("- ").append(feature).append("\n");
            }
        }

        prompt.append("\nChanged File Analysis:\n");

        if (evidence.getChangedFileInsights().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String insight : evidence.getChangedFileInsights()) {
                prompt.append("- ").append(insight).append("\n");
            }
        }

        prompt.append("\nFeatures:\n");

        if (evidence.getFeatures().isEmpty()) {
            prompt.append("- No extracted features available.\n");
        } else {

            for (Feature feature : evidence.getFeatures()) {

                prompt.append("\nFeature: ")
                        .append(feature.getName())
                        .append("\n");

                for (String item : feature.getEvidence()) {
                    prompt.append("- ")
                            .append(item)
                            .append("\n");
                }
            }
        }

        prompt.append("\nPull Requests:\n");

        if (evidence.getPullRequestTitles().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String pr : evidence.getPullRequestTitles()) {
                prompt.append("- ").append(pr).append("\n");
            }
        }

        prompt.append("""

========================
END OF EVIDENCE
========================

Instructions:

- Use Repository Analysis to understand the overall architecture.
- Use Features as the primary evidence.
- Use Technologies only if they appear in the evidence.
- If only one feature exists, build the STAR story around that feature.
- If there are no features but Repository Analysis exists, build the STAR story from the repository architecture.
- Never invent information.
- Never return empty strings.
- Return ONLY the JSON object.

Generate the STAR story now.
""");

        return prompt.toString();
    }
}