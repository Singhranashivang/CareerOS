package com.careeros.backend.achievement.engine;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits a filename or free text into lowercase word tokens — camelCase
 * boundaries broken apart, connector words dropped. Extracted out of
 * GroundingValidator so AchievementTitleSpecificityValidator uses the
 * identical tokenization instead of a second, silently-drifting copy (same
 * reasoning SourcePathHeuristics was pulled out of CodeStatsFetcher for).
 */
final class EvidenceTokenizer {

    /** Below this length a token is usually noise ("cpp", "the", "src"), not a claim-worthy term. */
    private static final int MIN_TOKEN_LENGTH = 4;

    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "have", "been", "were",
            "will", "your", "which", "when", "while", "using", "used", "user",
            "code", "file", "files", "work", "week", "repository", "project",
            "added", "adding", "made", "make", "built", "build", "create",
            "created", "creating", "update", "updated", "updating", "change",
            "changed", "changes", "implement", "implemented", "improve",
            "improved", "developed", "development"
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private EvidenceTokenizer() {
    }

    /** "SpiralSearch.cpp" -> "Spiral Search.cpp" -> spiral / search / cpp (cpp dropped, below MIN_TOKEN_LENGTH). */
    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String spaced = CAMEL_BOUNDARY.matcher(text).replaceAll("$1 $2");
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD.split(spaced.toLowerCase())) {
            if (token.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
