package com.careeros.backend.achievement.llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corporate filler banned from generated text, shared by the achievement
 * generation prompt (STAR fields) and the LinkedIn post prompt/guardrail.
 * Banning it at achievement generation is what stops it from ever reaching
 * the LinkedIn prompt as "evidence" to copy in the first place.
 *
 * For LinkedIn posts specifically, this is detection only — see
 * BannedVocabularyScrubber for the deterministic post-processing that fixes
 * what the prompt doesn't stop (a 10-generation reliability check found the
 * model still uses one of these terms in roughly 60% of first attempts;
 * prompting it away wasn't converging, so the fix moved to text replacement).
 */
public final class BannedVocabulary {

    /** Human-readable list, for embedding directly in a prompt. */
    public static final String PROMPT_LIST =
            "enhanced, robust, leveraged, utilized, comprehensive, cutting-edge, "
            + "amazing, incredible journey, significantly improved, seamless, "
            + "streamlined, unauthorized access, improved reliability";

    /**
     * One entry per banned term. Package-visible so BannedVocabularyScrubber
     * can reuse the exact same patterns for cleanup instead of maintaining a
     * second, driftable copy of this list.
     */
    enum Term {
        ENHANCED("enhanc\\w*"),
        ROBUST("robust\\w*"),
        LEVERAGED("leverag\\w*"),
        UTILIZED("utiliz\\w*"),
        COMPREHENSIVE("comprehensive\\w*"),
        CUTTING_EDGE("cutting-edge"),
        AMAZING("amazing"),
        INCREDIBLE_JOURNEY("incredible journey"),
        SIGNIFICANTLY("significantly"),
        SEAMLESS("seamless\\w*"),
        STREAMLINED("streamlin\\w*"),
        UNAUTHORIZED_ACCESS("unauthorized access"),
        IMPROVED_RELIABILITY("improved reliability");

        final Pattern pattern;

        Term(String regex) {
            this.pattern = Pattern.compile("\\b" + regex + "\\b", Pattern.CASE_INSENSITIVE);
        }
    }

    private BannedVocabulary() {
    }

    /** Distinct banned substrings actually present in the text, in the order they occur. */
    public static List<String> violationsIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Term term : Term.values()) {
            Matcher matcher = term.pattern.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return new ArrayList<>(found);
    }
}
