package com.careeros.backend.achievement.linkedin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedInPostShapeValidatorTest {

    private final LinkedInPostShapeValidator validator = new LinkedInPostShapeValidator();

    private static String words(int count) {
        return "word ".repeat(count).trim();
    }

    @Test
    void aSingleParagraphIsRejectedRegardlessOfLength() {
        // The real bug this was added for: one long paragraph, no blank lines.
        String post = words(200);

        var violations = validator.violationsIn(post);

        assertThat(violations).anyMatch(v -> v.contains("paragraph break"));
    }

    @Test
    void aShortButProperlyParagraphedPostHasNoWordCountViolation() {
        // No minimum word count — removed after it caused fabrication: the
        // model padded a too-short grounded post with an invented claim to
        // hit the old 140-word floor. A short, honest post is correct.
        String post = "One.\n\nTwo.\n\nThree.";

        var violations = validator.violationsIn(post);

        assertThat(violations).noneMatch(v -> v.contains("word"));
    }

    @Test
    void threeParagraphsAndEnoughWordsPassesCleanly() {
        String post = words(50) + "\n\n" + words(50) + "\n\n" + words(50);

        var violations = validator.violationsIn(post);

        assertThat(violations).isEmpty();
    }

    @Test
    void exactlyTwoParagraphBreaksIsTheMinimumAllowed() {
        String post = words(50) + "\n\n" + words(50) + "\n\n" + words(50);

        assertThat(post.split("\\r?\\n\\s*\\r?\\n")).hasSize(3); // sanity: exactly 2 breaks
        assertThat(validator.violationsIn(post)).isEmpty();
    }

    @Test
    void oneParagraphBreakIsRejected() {
        String post = words(75) + "\n\n" + words(75);

        var violations = validator.violationsIn(post);

        assertThat(violations).anyMatch(v -> v.contains("paragraph break"));
    }

    @Test
    void aBlankPostIsRejected() {
        assertThat(validator.violationsIn("")).isNotEmpty();
        assertThat(validator.violationsIn(null)).isNotEmpty();
    }

    @Test
    void fourParagraphsIsTheMaximumAllowed() {
        String post = words(30) + "\n\n" + words(30) + "\n\n" + words(30) + "\n\n" + words(30);

        assertThat(post.split("\\r?\\n\\s*\\r?\\n")).hasSize(4); // sanity: exactly 3 breaks

        assertThat(validator.violationsIn(post)).isEmpty();
    }

    @Test
    void fiveParagraphsExceedsTheMaximumAndIsRejected() {
        // The real bug this was added for: two real posts used all 5 slots
        // and read as a changelog that listed every piece of work instead of
        // picking two or three and going deep.
        String post = words(30) + "\n\n" + words(30) + "\n\n" + words(30) + "\n\n" + words(30) + "\n\n" + words(30);

        var violations = validator.violationsIn(post);

        assertThat(violations).anyMatch(v -> v.contains("paragraph breaks") && v.contains("pick two or three"));
    }
}
