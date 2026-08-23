package com.careeros.backend.achievement.linkedin;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches the prompt's paragraph-break instruction getting ignored — added
 * after a real period post came back as one block despite the prompt asking
 * for 3-5 paragraphs.
 *
 * No minimum word count: that check existed briefly and was removed. When
 * the model couldn't reach 140 words honestly from thin evidence, its retry
 * padded the gap with an invented claim ("significantly improved the
 * system's ability to handle large numbers of achievements concurrently")
 * that nothing in the evidence supported — the validation was manufacturing
 * exactly the fabrication problem the rest of this prompt exists to prevent.
 * A short, grounded post is correct; there is nothing to enforce a floor
 * against.
 */
@Component
public class LinkedInPostShapeValidator {

    /** 2 breaks = 3 paragraphs, the low end of "3 to 5". */
    static final int MIN_PARAGRAPH_BREAKS = 2;

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\r?\\n\\s*\\r?\\n");

    /** Empty when the post's shape is fine. Human-readable problem descriptions otherwise. */
    public List<String> violationsIn(String post) {

        List<String> violations = new ArrayList<>();

        if (post == null || post.isBlank()) {
            violations.add("post is empty");
            return violations;
        }

        int paragraphBreaks = countMatches(PARAGRAPH_BREAK, post);
        if (paragraphBreaks < MIN_PARAGRAPH_BREAKS) {
            violations.add(("only %d paragraph break(s) in the post — needs at least %d "
                    + "(blank line between each of 3+ paragraphs)")
                    .formatted(paragraphBreaks, MIN_PARAGRAPH_BREAKS));
        }

        return violations;
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
