package com.careeros.backend.achievement.weekly;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.springframework.stereotype.Component;

@Component
public class WeeklyPromptBuilder {

    public String build(Evidence evidence) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are a software engineer writing your own weekly engineering update for your manager.

Your goal is to write exactly how a real developer would summarize their week's work after reviewing their GitHub activity.

========================
WRITING STYLE
========================

- Write naturally.
- Write in first person.
- Use simple, direct language.
- Describe only the work actually completed.
- Explain what you built, improved or investigated.
- Keep the summary between 60 and 120 words.
- Sound like a real developer, not an AI assistant.
- Use past tense.
- Be confident but not exaggerated.

Avoid phrases like:

- focused on
- worked on various tasks
- contributed to
- implemented new functionalities
- leveraged
- utilized
- cutting-edge
- robust
- comprehensive
- innovative solution
- significantly improved
- successfully implemented

Instead, describe the actual engineering work.

GOOD SUMMARY

This week I added an F1 Race Predictor to the Hacktoberfest repository and implemented several algorithm solutions, including Merge Sort and Maximum Product of Two Elements. I also updated the contributor guide and started testing the shortest path implementation.

BAD SUMMARY

This week I focused on implementing several innovative solutions while leveraging modern technologies to improve engineering efficiency.

========================
TITLE
========================

Generate a short descriptive title.

Good examples

- Hacktoberfest Repository Progress
- Backend Development Update
- Algorithm Development Progress
- Open Source Contribution Progress

Avoid generic titles such as

- Weekly Engineering Summary
- Weekly Report
- Development Summary

========================
SUMMARY
========================

The summary should naturally answer:

1. What did I build?
2. What did I improve?
3. What am I currently working on?

========================
HIGHLIGHTS
========================

Generate 3-6 highlights.

Each highlight should contain between 5 and 12 words.

Examples

- Added F1 Race Predictor
- Implemented Merge Sort
- Improved contributor documentation
- Started shortest path testing

========================
TECHNOLOGIES
========================

Include ONLY technologies explicitly present in the evidence.

Examples

✓ Java
✓ Python
✓ React
✓ Spring Boot

Do NOT include

✗ Algorithms
✗ Backend Development
✗ Software Engineering
✗ Problem Solving

========================
STRICT RULES
========================

- Use ONLY the evidence below.
- Never invent repositories.
- Never invent features.
- Never invent technologies.
- Never invent pull requests.
- Never invent metrics.
- Never invent business impact.
- Never exaggerate work.
- If evidence is missing, simply omit it.
- Never hallucinate.

Return ONLY valid JSON.

{
  "title":"",
  "summary":"",
  "highlights":[],
  "technologies":[],
  "confidence":0.95
}

========================
EVIDENCE
========================

Repository:
""");

        prompt.append(evidence.getRepositoryName());

        prompt.append("\n\nDescription:\n");
        prompt.append(
                evidence.getDescription() == null
                        ? "Not available"
                        : evidence.getDescription()
        );

        prompt.append("\n\nTechnologies:\n");

        if (evidence.getTechnologies().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String tech : evidence.getTechnologies()) {
                prompt.append("- ")
                        .append(tech)
                        .append("\n");
            }
        }

        prompt.append("\nRepository Analysis:\n");

        if (evidence.getRepositoryFeatures().isEmpty()) {
            prompt.append("- None\n");
        } else {
            for (String feature : evidence.getRepositoryFeatures()) {
                prompt.append("- ")
                        .append(feature)
                        .append("\n");
            }
        }

        prompt.append("\nFeatures:\n");

        if (evidence.getFeatures().isEmpty()) {
            prompt.append("- None\n");
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
                prompt.append("- ")
                        .append(pr)
                        .append("\n");
            }
        }

        prompt.append("""

========================
END OF EVIDENCE
========================

Generate the weekly update now.

Remember:

- Sound like a real software engineer.
- Base everything on the evidence.
- Never invent information.
- Return ONLY the JSON object.
""");

        return prompt.toString();
    }

}