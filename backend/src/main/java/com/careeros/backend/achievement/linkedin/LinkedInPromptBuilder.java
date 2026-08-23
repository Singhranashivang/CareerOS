package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.llm.BannedVocabulary;
import com.careeros.backend.achievement.record.AchievementEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class LinkedInPromptBuilder {

    /** A period prompt could otherwise list dozens of achievements — same reasoning as AchievementPromptBuilder's caps. */
    private static final int MAX_PERIOD_ACHIEVEMENTS = 12;

    /**
     * Identical for both prompts: same banned list, same first-person plain
     * voice, same solo-author rule. Kept as one block so the single-achievement
     * and period-summary prompts can't drift apart on the rules that are
     * supposed to be shared between them. Deliberately doesn't call out
     * "headline" specifically — the period prompt has no headline field at
     * all (see PERIOD_OUTPUT_JSON), so "anywhere in the output" has to mean
     * that generically for both.
     */
    private static final String VOICE_AND_RULES = """
            VOICE

            SOLO AUTHOR — the work described was done by ONE person working
            alone. Never reference a team, colleagues, collaboration, code
            review, stakeholders, or how the work was received by anyone
            else. There is no "we" — only "I". If the evidence doesn't name
            another specific person, no other person was involved, and the
            post must not imply one existed.

            - First person, past tense, plain.
            - Name the actual project and the actual classes, files, or systems given
              in the evidence. Do not talk about the work in the abstract.
            - Banned words and phrases, in any form, anywhere in the output: """
            + BannedVocabulary.PROMPT_LIST
            + """
            .
            - No hashtags. No emoji. No "excited to share", "thrilled to", "honored to",
              "delighted to".
            """;

    private static final String OUTPUT_JSON = """
            OUTPUT

            "headline" must be copied verbatim from one line of "post" — not a
            separate summary you compose. Pick whichever single line you already
            wrote in "post" is the strongest hook, and repeat that exact text as
            "headline" too. Do not write a new sentence for it, and do not invent a
            narrative frame like "My Journey in X" or "Reflections on Y" — if the
            text isn't already a line inside "post", it does not belong in "headline".

            Return ONLY JSON, no other text:

            {
              "headline":"",
              "post":"",
              "confidence":0.95
            }

            ====================
            """;

    /** No "headline" key at all — a period post's headline was always a redundant copy of the post's first line. */
    private static final String PERIOD_OUTPUT_JSON = """
            OUTPUT

            Return ONLY JSON, no other text:

            {
              "post":"",
              "confidence":0.95
            }

            ====================
            """;

    private static final String HEADER = """
            You are a software engineer writing a LinkedIn post about one specific
            piece of work you just finished.

            The post must read like a developer talking, not a resume bullet and not
            a press release. A single dense paragraph is a failure regardless of how
            accurate it is.

            SHAPE

            - 3 to 5 short paragraphs, separated by a blank line each. Never one block of text.
              Get there by BREAKING your material at its natural boundaries — the
              before-state, the change, the result, the closing line each make a
              natural paragraph of their own. Never by adding sentences, detail, or
              claims that aren't in the evidence just to fill out more paragraphs.
            - Open with the single most specific or surprising fact from the evidence.
              Never open with a summary of "this week" or the category of work.
            - Close with one line worth remembering: a lesson, a trade-off, or a question.
            - Under 250 words. There is no minimum — a shorter, honest post beats a
              longer one padded with a claim the evidence doesn't support.

            """
            + VOICE_AND_RULES
            + """

            SUBSTANCE

            - Lead with the specific detail, not the category. "Two controllers were
              still resolving the user inline" beats "identified vulnerabilities in the
              authentication flow".
            - State what was actually wrong or missing before, not only what was done.
            - Use ONLY the facts in the provided evidence. Never invent achievements,
              technologies, or business impact. Never claim an improvement or metric
              that doesn't appear in the input.
            - The evidence text below was written by someone else and may itself
              contain the banned words above (it often does). Take only the facts from
              it — what was broken, what you changed, what file or class it lived in —
              and re-describe them in your own plain words. Copying its vocabulary,
              including a banned word, is still a violation even though the word was
              "in the evidence".
            - Some sections below may be missing — work only from the ones present.
              A missing section is not something to invent or pad around.

            GOOD EXAMPLES (different kinds of work — match this register, not this content)

            ✓ Bug fix:
            Two controllers were still pulling the user id straight off the session
            instead of going through CurrentUserService. Easy to miss, easy to exploit —
            a forged session could touch data that wasn't scoped to the right account.

            Went through every controller by hand and rerouted them through the shared
            service. No new dependency, just deleting the shortcuts.

            The parts of a codebase that look "basically fine" are usually the ones
            nobody re-checked in a year.

            ✓ Refactor:
            LinkedInPostService was caching one post per user, forever. The first
            achievement generated set the copy for every achievement after it, and
            nobody noticed because the text still looked plausible.

            Moved the cache key from user to achievement id and added a unique index so
            regeneration overwrites instead of piling up rows.

            The bug wasn't in the LLM call. It was in what we picked as a cache key.

            ✓ New feature:
            Added a claim loop to the scheduler using SELECT ... FOR UPDATE SKIP LOCKED
            so two workers can't grab the same scheduled post.

            Tested it by firing 20 threads at the same table and checking nothing got
            claimed twice.

            Postgres already had the tool for this. I just had to stop reaching for an
            app-level lock first.

            """
            + OUTPUT_JSON;

    private static final String PERIOD_HEADER = """
            You are a software engineer writing a LinkedIn post looking back over a
            period of work, drawn from several achievements — not one specific change.

            The post must read like a developer talking, not a changelog and not a
            press release. A single dense paragraph, or a list of everything you did,
            is a failure regardless of how accurate it is.

            SHAPE

            - 3 to 5 short paragraphs, separated by a blank line each. Never one block of text.
              Get there by BREAKING your material at its natural boundaries — the
              opening fact, each piece of work, the closing line each make a natural
              paragraph of their own. Never by adding sentences, detail, or claims
              that aren't in the achievements below just to fill out more paragraphs.
            - Open with the single most specific or surprising thing from the period —
              not a summary of "this period" or a count of how much got done.
            - The middle names two or three concrete pieces of work from the
              achievements below, using the real class, file, or system names they
              give — not a generic restatement like "made several improvements".
              You do not need to mention every achievement listed; picking two or
              three and going deep beats listing all of them.
            - Close with one line worth remembering: a lesson, a trade-off, or a question.
            - Under 250 words. There is no minimum — a shorter, honest post beats a
              longer one padded with a claim the achievements don't support.

            """
            + VOICE_AND_RULES
            + """

            SUBSTANCE

            - Lead with the specific detail, not the category.
            - Use ONLY the facts in the achievements listed below. Never invent work,
              technologies, or business impact. Never claim an improvement or metric
              that doesn't appear in the input.
            - Each achievement's text below was written by someone else (or a
              previous generation pass) and may itself contain the banned words above.
              Take only the facts — what repository, what changed, what file or class
              it lived in — and re-describe them in your own plain words. Copying its
              vocabulary, including a banned word, is still a violation.

            GOOD EXAMPLE (register only — not this content)

            ✓ The CSRF work turned out to be the easy half of the month. The harder
            part was noticing GithubRepository.user had no @JsonIgnore, and neither
            did half the other lazy relations once I went looking.

            Fixed the CSRF gap with a cookie token repository in SecurityConfig, and
            added a reflective test that walks every controller's return type looking
            for a JPA entity, so the next missing @JsonIgnore fails a build instead of
            a live request.

            The bug that bites you twice is worth a test that isn't scoped to just
            the one field you happened to fix.

            """
            + PERIOD_OUTPUT_JSON;

    public String build(AchievementEntity achievement) {
        return HEADER + evidenceSection(achievement) + "\nReturn JSON only.\n";
    }

    /**
     * Only the fields that are actually present. An achievement generated by
     * the weekly pipeline has just title + summary (its schema deliberately
     * never asks for resumeBullet/STAR — see AchievementEnginePromptBuilder);
     * one from the per-repo generator has title + resumeBullet + STAR and no
     * summary. Printing an empty "Situation:" heading for a field that isn't
     * there invites the model to invent content for it — omitting the
     * heading entirely is the only way to actually withhold it.
     */
    private String evidenceSection(AchievementEntity achievement) {

        StringBuilder section = new StringBuilder("\nTitle:\n").append(achievement.getTitle()).append("\n");

        boolean hasResumeBullet = isPresent(achievement.getResumeBullet());
        boolean hasStar = isPresent(achievement.getStarSituation())
                || isPresent(achievement.getStarTask())
                || isPresent(achievement.getStarAction())
                || isPresent(achievement.getStarResult());

        appendIfPresent(section, "Resume bullet", achievement.getResumeBullet());
        appendIfPresent(section, "Situation", achievement.getStarSituation());
        appendIfPresent(section, "Task", achievement.getStarTask());
        appendIfPresent(section, "Action", achievement.getStarAction());
        appendIfPresent(section, "Result", achievement.getStarResult());
        appendIfPresent(section, "Summary", achievement.getSummary());

        // Working from strictly less than usual — say so, rather than let the
        // post come out the same length and shape as a fully-detailed one.
        if (!hasResumeBullet && !hasStar) {
            section.append("""

                    NOTE: Only a title and summary are available for this achievement —
                    no resume bullet or STAR breakdown was generated for it. Write from
                    this limited evidence only; a shorter, plainer post is correct here,
                    not a reason to invent supporting detail.
                    """);
        }

        return section.toString();
    }

    private static void appendIfPresent(StringBuilder section, String label, String value) {
        if (isPresent(value)) {
            section.append("\n").append(label).append(":\n").append(value).append("\n");
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * One retry after a violation — a banned word, or the SHAPE rules (paragraph
     * count, word count) getting ignored. problems is a list of specific,
     * human-readable descriptions (see LinkedInPostService.problemsIn), not
     * just banned words, so this phrasing has to stay generic enough to cover
     * either kind.
     */
    public String buildRetry(AchievementEntity achievement, List<String> problems) {
        return build(achievement) + retrySuffix(problems);
    }

    public String buildPeriod(List<AchievementEntity> achievements, LocalDate from, LocalDate to) {
        return PERIOD_HEADER + periodEvidenceSection(achievements, from, to) + "\nReturn JSON only.\n";
    }

    public String buildPeriodRetry(
            List<AchievementEntity> achievements, LocalDate from, LocalDate to, List<String> problems) {
        return buildPeriod(achievements, from, to) + retrySuffix(problems);
    }

    private static String retrySuffix(List<String> problems) {
        return "\n\nYour previous attempt had these problems: "
                + String.join("; ", problems)
                + ". Rewrite the whole post from scratch fixing them, following the same "
                + "SHAPE, VOICE, and SUBSTANCE rules above. Return ONLY JSON in the same shape.";
    }

    private String periodEvidenceSection(List<AchievementEntity> achievements, LocalDate from, LocalDate to) {

        StringBuilder section = new StringBuilder("\nPeriod: ")
                .append(from).append(" to ").append(to).append("\n\nAchievements:\n");

        int shown = Math.min(achievements.size(), MAX_PERIOD_ACHIEVEMENTS);
        for (int i = 0; i < shown; i++) {
            AchievementEntity achievement = achievements.get(i);
            String repository = isPresent(achievement.getRepositoryName())
                    ? achievement.getRepositoryName() : "unknown repository";
            // resumeBullet over the full STAR breakdown here on purpose: this
            // prompt can carry up to a dozen achievements, and resumeBullet is
            // already the single grounded sentence naming the real work — the
            // right level of detail per item at this volume.
            String detail = isPresent(achievement.getResumeBullet())
                    ? achievement.getResumeBullet()
                    : isPresent(achievement.getSummary()) ? achievement.getSummary() : achievement.getTitle();

            section.append(i + 1).append(". [").append(repository).append("] ")
                    .append(achievement.getTitle()).append(" — ").append(detail).append("\n");
        }

        int remaining = achievements.size() - shown;
        if (remaining > 0) {
            section.append("...and ").append(remaining).append(" more achievement")
                    .append(remaining == 1 ? "" : "s").append(" in this period.\n");
        }

        return section.toString();
    }
}
