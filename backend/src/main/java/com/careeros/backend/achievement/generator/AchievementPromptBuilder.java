package com.careeros.backend.achievement.generator;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.knowledge.RepositoryKnowledge;
import com.careeros.backend.achievement.llm.BannedVocabulary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AchievementPromptBuilder {

    /**
     * Ollama truncates an over-length prompt from the front, keeping the
     * tail nearest generation — see LLMService. Two defenses against that:
     * the JSON schema now lives at the END of the prompt, immediately before
     * "generate now", so it survives truncation instead of being the first
     * thing dropped; and evidence lists are capped so a 176-file repo like
     * CareerOS doesn't need truncation to begin with.
     */
    private static final int MAX_CHANGED_FILES = 30;
    private static final int MAX_FEATURE_EVIDENCE = 8;

    /** Retry path: a repo that still drifts at the normal caps gets a much smaller prompt. */
    private static final int SHORTENED_MAX_CHANGED_FILES = 10;
    private static final int SHORTENED_MAX_FEATURE_EVIDENCE = 3;

    public String build(RepositoryKnowledge knowledge, Evidence evidence) {
        return build(knowledge, evidence, MAX_CHANGED_FILES, MAX_FEATURE_EVIDENCE);
    }

    /** Used for the one retry on schema drift, per AchievementGeneratorService. */
    public String buildShortened(RepositoryKnowledge knowledge, Evidence evidence) {
        return build(knowledge, evidence, SHORTENED_MAX_CHANGED_FILES, SHORTENED_MAX_FEATURE_EVIDENCE);
    }

    private String build(
            RepositoryKnowledge knowledge,
            Evidence evidence,
            int maxChangedFiles,
            int maxFeatureEvidence
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
- Banned words and phrases, in any form, in the title, resumeBullet, and every
  STAR field: """ + BannedVocabulary.PROMPT_LIST + """
.

GOOD EXAMPLES

✓ Developed an F1 Race Predictor in Python for the Hacktoberfest repository and added multiple algorithm implementations to expand project functionality.

✓ Created a contributor guide and implemented several algorithmic solutions, including Merge Sort and shortest path implementations.

BAD EXAMPLES

✗ Successfully leveraged cutting-edge technologies to improve repository performance.

✗ Implemented innovative solutions that significantly enhanced system efficiency.

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

            appendCapped(prompt, feature.getEvidence(), maxFeatureEvidence, "commit", " - ");
        }

        prompt.append("\nChanged Files:\n");

        appendCapped(prompt, evidence.getChangedFiles(), maxChangedFiles, "file", "- ");

        prompt.append("\nPull Requests:\n");

        for (String pr : evidence.getPullRequestTitles()) {
            prompt.append("- ").append(pr).append("\n");
        }

        // The schema lives here, at the end, right before generation starts —
        // not at the top. See the class javadoc for why.
        prompt.append("""

=========================
Output
=========================

Generate ONE engineering achievement from the evidence above.

Return ONLY valid JSON, in exactly this shape:

{
  "title":"",
  "resumeBullet":"",
  "starSituation":"",
  "starTask":"",
  "starAction":"",
  "starResult":"",
  "confidence":0.95
}

IF THE EVIDENCE IS TOO THIN

If the evidence above does not support a specific, grounded claim — a single
trivial commit, only file uploads, only README or formatting edits — return
exactly this instead:

{"insufficient": true, "reason": "<one sentence naming what is missing>"}

Returning insufficient is a correct answer. Do not invent work to fill the gap,
and do not describe the repository itself as if it were your achievement.

Return ONLY the JSON object now.
""");

        return prompt.toString();
    }

    private static void appendCapped(
            StringBuilder prompt, List<String> items, int max, String noun, String prefix
    ) {
        int shown = Math.min(items.size(), max);
        for (int i = 0; i < shown; i++) {
            prompt.append(prefix).append(items.get(i)).append("\n");
        }
        int remaining = items.size() - shown;
        if (remaining > 0) {
            prompt.append(prefix).append("...and ").append(remaining).append(" more ").append(noun)
                    .append(remaining == 1 ? "" : "s").append("\n");
        }
    }

}
