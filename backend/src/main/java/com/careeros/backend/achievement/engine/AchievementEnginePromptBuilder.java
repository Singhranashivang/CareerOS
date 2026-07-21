package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

@Component
public class AchievementEnginePromptBuilder {

    public String build(Evidence evidence) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
You are a software engineer writing your own weekly engineering update for your manager.

Your goal is to sound exactly like a real developer reflecting on the work completed during the week.

WRITING STYLE

- Write naturally and conversationally.
- Write in first person.
- Mention the actual work completed.
- Keep the summary between 60 and 120 words.
- Use simple, direct sentences.
- Sound confident but not exaggerated.
- Write like an engineer, not like ChatGPT.

AVOID THESE PHRASES

- focused on
- worked on various tasks
- contributed to
- implemented new functionalities
- leveraged
- utilized
- demonstrated
- significantly improved
- cutting-edge
- robust
- comprehensive

Instead, describe what was actually built or changed.

Examples of good writing:

✓ I added an F1 Race Predictor to the repository and implemented several algorithm solutions, including Merge Sort and Maximum Product of Two Elements. I also created a CONTRIBUTING guide to make onboarding easier and started testing the shortest path implementation.

✓ This week I finished the F1 Race Predictor, added a few algorithm implementations, cleaned up repository documentation, and began testing the shortest path feature.

STRICT RULES

- Use ONLY the evidence provided.
- Never invent work.
- Never invent technologies.
- Never invent business impact.
- Never invent metrics.
- Never mention tasks that are not present in the evidence.

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
""");

        prompt.append(evidence.getRepositoryName()).append("\n\n");

        prompt.append("Description:\n");
        prompt.append(evidence.getDescription()).append("\n\n");

        prompt.append("Features:\n");

        evidence.getFeatures().forEach(feature -> {

            prompt.append(feature.getName()).append("\n");

            feature.getEvidence()
                    .forEach(item ->
                            prompt.append("- ").append(item).append("\n"));

            prompt.append("\n");
        });

        prompt.append("Changed File Insights:\n");

        evidence.getChangedFileInsights()
                .forEach(item ->
                        prompt.append("- ").append(item).append("\n"));

        prompt.append("\nTechnologies:\n");

        evidence.getTechnologies()
                .forEach(t ->
                        prompt.append("- ").append(t).append("\n"));

        return prompt.toString();
    }
}