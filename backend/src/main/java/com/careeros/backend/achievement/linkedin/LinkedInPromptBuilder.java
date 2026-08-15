package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.llm.BannedVocabulary;
import com.careeros.backend.achievement.record.AchievementEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LinkedInPromptBuilder {

    public String build(AchievementEntity achievement) {

        return """
                You are a software engineer writing a LinkedIn post about one specific
                piece of work you just finished.

                The post must read like a developer talking, not a resume bullet and not
                a press release. A single dense paragraph is a failure regardless of how
                accurate it is.

                SHAPE

                - 3 to 5 short paragraphs, separated by a blank line each. Never one block of text.
                - Open with the single most specific or surprising fact from the evidence.
                  Never open with a summary of "this week" or the category of work.
                - Close with one line worth remembering: a lesson, a trade-off, or a question.
                - Under 150 words total.

                VOICE

                - First person, past tense, plain.
                - Name the actual project and the actual classes, files, or systems given
                  in the evidence. Do not talk about the work in the abstract.
                - Banned words and phrases, in any form, anywhere in the output —
                  the headline is not exempt: """
                + BannedVocabulary.PROMPT_LIST
                + """
                .
                - No hashtags. No emoji. No "excited to share", "thrilled to", "honored to",
                  "delighted to".

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

                OUTPUT

                Return ONLY JSON, no other text:

                {
                  "headline":"",
                  "post":"",
                  "confidence":0.95
                }

                ====================

                Title:
                """
                + achievement.getTitle()

                + """

                Resume bullet:
                """
                + achievement.getResumeBullet()

                + """

                Situation:
                """
                + achievement.getStarSituation()

                + """

                Task:
                """
                + achievement.getStarTask()

                + """

                Action:
                """
                + achievement.getStarAction()

                + """

                Result:
                """
                + achievement.getStarResult()

                + """

                Return JSON only.
                """;

    }

    /** One retry after a banned-word violation: same prompt, plus the exact words to cut. */
    public String buildRetry(AchievementEntity achievement, List<String> usedBannedWords) {
        return build(achievement)
                + "\n\nYour previous attempt used these banned words: "
                + String.join(", ", usedBannedWords)
                + ". Rewrite the whole post from scratch without them, following the same "
                + "SHAPE, VOICE, and SUBSTANCE rules above. Return ONLY JSON in the same shape.";
    }
}
