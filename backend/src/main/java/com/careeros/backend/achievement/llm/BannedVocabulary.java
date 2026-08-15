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
 */
public final class BannedVocabulary {

    /** Human-readable list, for embedding directly in a prompt. */
    public static final String PROMPT_LIST =
            "enhanced, robust, leveraged, utilized, comprehensive, cutting-edge, "
            + "amazing, incredible journey, significantly improved, seamless, "
            + "streamlined, unauthorized access, improved reliability";

    // Stemmed so inflections (enhancing, leverages, streamlined...) are
    // caught too, not just the exact forms listed above.
    private static final List<Pattern> PATTERNS = List.of(
            "enhanc\\w*", "robust\\w*", "leverag\\w*", "utiliz\\w*", "comprehensive\\w*",
            "cutting-edge", "amazing", "incredible journey", "significantly",
            "seamless\\w*", "streamlin\\w*", "unauthorized access", "improved reliability"
    ).stream()
            .map(w -> Pattern.compile("\\b" + w + "\\b", Pattern.CASE_INSENSITIVE))
            .toList();

    private BannedVocabulary() {
    }

    /** Distinct banned substrings actually present in the text, in the order they occur. */
    public static List<String> violationsIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return new ArrayList<>(found);
    }
}
