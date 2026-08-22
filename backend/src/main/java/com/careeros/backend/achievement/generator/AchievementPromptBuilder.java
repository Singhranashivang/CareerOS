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
    private static final int MAX_DIFFS = 15;

    /** Retry path: a repo that still drifts at the normal caps gets a much smaller prompt. */
    private static final int SHORTENED_MAX_CHANGED_FILES = 10;
    private static final int SHORTENED_MAX_FEATURE_EVIDENCE = 3;
    private static final int SHORTENED_MAX_DIFFS = 5;

    public String build(RepositoryKnowledge knowledge, Evidence evidence) {
        return build(knowledge, evidence, null, null);
    }

    /**
     * priorTitle/priorResumeBullet, when non-null, are an existing achievement
     * for this repository whose subject the new cluster's evidence overlaps
     * with (see AchievementSemanticDedupeValidator) — the model is told to
     * describe only what's new relative to it, rather than re-describing the
     * same subsystem from scratch.
     */
    public String build(RepositoryKnowledge knowledge, Evidence evidence, String priorTitle, String priorResumeBullet) {
        return build(knowledge, evidence, priorTitle, priorResumeBullet,
                MAX_CHANGED_FILES, MAX_FEATURE_EVIDENCE, MAX_DIFFS);
    }

    /** Used for the one retry on schema drift, per AchievementGeneratorService. */
    public String buildShortened(RepositoryKnowledge knowledge, Evidence evidence) {
        return buildShortened(knowledge, evidence, null, null);
    }

    public String buildShortened(RepositoryKnowledge knowledge, Evidence evidence, String priorTitle, String priorResumeBullet) {
        return build(knowledge, evidence, priorTitle, priorResumeBullet,
                SHORTENED_MAX_CHANGED_FILES, SHORTENED_MAX_FEATURE_EVIDENCE, SHORTENED_MAX_DIFFS);
    }

    private String build(
            RepositoryKnowledge knowledge,
            Evidence evidence,
            String priorTitle,
            String priorResumeBullet,
            int maxChangedFiles,
            int maxFeatureEvidence,
            int maxDiffs
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
- Keep the wording concise and natural.
- Describe what the developer built or changed — never what the software
  does when it runs. The evidence below is source code and commit history,
  not a report of the program executing. A log message, a status string a
  method returns, or text a command prints is the PROGRAM talking about
  itself, not you describing your work — never quote, paraphrase, or
  summarize that as if it were the achievement.
- Banned words and phrases, in any form, in every field: """ + BannedVocabulary.PROMPT_LIST + """
.

GOOD EXAMPLES

✓ Developed an F1 Race Predictor in Python for the Hacktoberfest repository and added multiple algorithm implementations to expand project functionality.

✓ Created a contributor guide and implemented several algorithmic solutions, including Merge Sort and shortest path implementations.

BAD EXAMPLES

✗ Successfully leveraged cutting-edge technologies to improve repository performance.

✗ Implemented innovative solutions that significantly enhanced system efficiency.

✗ Synced all commits for two repositories, and both repositories now reflect the full commit history. (This describes the running program's output, not the code that was written — the achievement is the sync logic that was built, not what it prints when it runs.)

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
""");

        // Highest-signal evidence first: the author's own description of the
        // work, if any commit in this cluster belongs to a pull request.
        if (evidence.getPullRequestTitle() != null && !evidence.getPullRequestTitle().isBlank()) {
            prompt.append("\nAuthor's Own Description (Pull Request):\n");
            prompt.append("Title: ").append(evidence.getPullRequestTitle()).append("\n");
            if (evidence.getPullRequestBody() != null && !evidence.getPullRequestBody().isBlank()) {
                prompt.append("Body: ").append(evidence.getPullRequestBody()).append("\n");
            }
        }

        prompt.append("\nCommit Messages (grouped by theme):\n");

        for (Feature feature : evidence.getFeatures()) {

            prompt.append("\nFeature: ")
                    .append(feature.getName())
                    .append("\n");

            appendCapped(prompt, feature.getEvidence(), maxFeatureEvidence, "commit", " - ");
        }

        prompt.append("\nChanged Files:\n");

        appendCapped(prompt, evidence.getChangedFiles(), maxChangedFiles, "file", "- ");

        if (!evidence.getDeletedFiles().isEmpty()) {
            // Removal is often the achievement — surfaced explicitly rather than
            // buried as one more line in Changed Files.
            prompt.append("\nDeleted Files:\n");
            for (String file : evidence.getDeletedFiles()) {
                prompt.append("- ").append(file).append("\n");
            }
        }

        if (!evidence.getAddedTestFiles().isEmpty()) {
            // Evidence of verification — what the author thought could break.
            prompt.append("\nAdded Test Files:\n");
            for (String file : evidence.getAddedTestFiles()) {
                prompt.append("- ").append(file).append("\n");
            }
        }

        if (!evidence.getDiffs().isEmpty()) {
            // Raw source, explicitly labelled: a diff can contain a log call
            // or a returned status string verbatim (e.g. a line like
            // `return "Synced " + count + " repositories";`), and reading
            // that as prose describing the work — rather than as code that
            // builds a runtime message — is exactly the failure this label
            // and the WRITING STYLE rule above exist to prevent.
            prompt.append("\nDiffs (raw source code, not prose — a quoted string inside a diff, "
                    + "like a log message or a returned status message, is text the PROGRAM "
                    + "prints when it runs, not a description of the work):\n");
            appendCapped(prompt, evidence.getDiffs(), maxDiffs, "diff", "");
        }

        if (priorTitle != null && !priorTitle.isBlank()) {
            prompt.append("""

=========================
Prior Work On This Subsystem
=========================

An existing achievement already describes earlier work that touches the
same part of the codebase as the evidence above:

""");
            prompt.append("Title: ").append(priorTitle).append("\n");
            if (priorResumeBullet != null && !priorResumeBullet.isBlank()) {
                prompt.append("Summary: ").append(priorResumeBullet).append("\n");
            }
            prompt.append("""

Describe ONLY what is new in the evidence above relative to that prior
work — a different mechanism, a different file, a different behavior. Do
not restate the prior work, and do not describe the same change again in
different words. Two pieces of work on the same subsystem, done at
different times, are two achievements — but only if the evidence above
actually shows something the prior achievement didn't already cover. If it
doesn't — if the evidence above is the same ground already described —
return the insufficient response instead of repeating it.
""");
        }

        // The schema lives here, at the end, right before generation starts —
        // not at the top. See the class javadoc for why.
        prompt.append("""

=========================
Output
=========================

Generate an engineering achievement grounded ONLY in the evidence above.

FIELD RULES

Every field is OPTIONAL except title. Send "" for any field the evidence
does not support. An omitted field is correct — it means you found nothing
to say there. An invented field is a failure. Two grounded sentences beat
six padded ones.

Every sentence you write must be traceable to something in the evidence
above: a specific file, class, method, commit message, dependency, or diff
line. If you cannot point to where a sentence comes from, do not write it.

BANNED, in every field:
- Any mention of teams, colleagues, stakeholders, users, or how the work was
  received. This evidence is one author's git history — it contains no
  record of who used the result or how they reacted to it.
- Any claim about impact, improvement, risk, engagement, or experience,
  unless a number for it appears in the evidence above. No number in the
  evidence, no claim in the output.
- Describing the software's runtime behaviour — what it prints, logs,
  returns, or displays when it runs — as if that were the work. "Synced 3
  repositories" is what the program says at runtime; it is not something
  the developer built. Describe the code that does the syncing instead.
- Any technology, framework, or library not present in the dependency
  files, imports, or Technologies list above.

WHAT EACH FIELD MEANS

title: A short label for the work, grounded in what actually changed.

resumeBullet: One sentence, resume-style, naming the concrete work — the
  specific file, feature, or mechanism, not a category of it.

starSituation: The state of the code BEFORE this work, only if the evidence
  shows a clear before-state (a bug, a missing capability, a limit). Omit
  if the evidence only shows what was built, not what was wrong before it.

starTask: What needed to happen, only if that is distinct information from
  starSituation and starAction. It is usually redundant with starAction —
  omit it rather than repeat the same fact in different words.

starAction: The specific mechanism — what was built, changed, or removed,
  and how. This is usually the field with the most support in the evidence.

starResult: What now works differently, stated mechanically, in the same
  concrete terms as the evidence — not what it means or how it felt.
  Example: "Repositories with deep history now record every commit instead
  of the first 30" is correct. "Improved data completeness" is not — it
  claims a meaning the evidence never measured. Omit this field entirely if
  the evidence shows only that something was built, not what changed as a
  result of building it.

Return ONLY valid JSON, in exactly this shape (empty string for any field
the evidence does not support):

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
