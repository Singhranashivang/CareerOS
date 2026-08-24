package com.careeros.backend.achievement.llm;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic cleanup for whatever BannedVocabulary.violationsIn still
 * finds after generation — see that class's javadoc for why this replaced
 * asking the model to avoid the words itself. Every term gets one of three
 * treatments:
 *
 * - a fixed neutral substitute (REPLACEMENTS), case-matched to the original
 *   word so a sentence-initial "Enhanced" becomes "Changed", not "changed";
 * - deleted outright, for adjectives/adverbs where removing the word leaves
 *   a grammatical sentence ("a robust test" -> "a test");
 * - the whole sentence it's in removed (STRIP_SENTENCE_TERMS), for the one
 *   phrase (a filler noun phrase, not a modifier) where deleting just the
 *   words would leave a dangling fragment ("It's been an incredible
 *   journey." -> "It's been an.").
 */
public final class BannedVocabularyScrubber {

    // ponytail: a fixed word per term, not conjugation-aware — "enhancing"
    // becomes "changed" same as "enhanced" does. Deterministic and always
    // banned-word-free, occasionally a slightly off tense. Upgrade path:
    // match the matched text's suffix (-ing/-ed/-s) and inflect the
    // replacement to match, if that ever matters more than it does now.
    private static final Map<BannedVocabulary.Term, String> REPLACEMENTS = Map.of(
            BannedVocabulary.Term.ENHANCED, "changed",
            BannedVocabulary.Term.LEVERAGED, "used",
            BannedVocabulary.Term.UTILIZED, "used",
            BannedVocabulary.Term.STREAMLINED, "simplified",
            BannedVocabulary.Term.UNAUTHORIZED_ACCESS, "improper access",
            BannedVocabulary.Term.IMPROVED_RELIABILITY, "more reliability"
    );

    // ponytail: sentence-level removal, not the finer comma-clause removal a
    // human editor might do — simpler and never leaves a broken fragment.
    // Only "incredible journey" needs it; if a future banned term also needs
    // this treatment, add it here.
    private static final Set<BannedVocabulary.Term> STRIP_SENTENCE_TERMS =
            Set.of(BannedVocabulary.Term.INCREDIBLE_JOURNEY);

    private BannedVocabularyScrubber() {
    }

    public static String scrub(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;
        for (BannedVocabulary.Term term : STRIP_SENTENCE_TERMS) {
            result = stripSentencesContaining(result, term.pattern);
        }
        for (BannedVocabulary.Term term : BannedVocabulary.Term.values()) {
            if (STRIP_SENTENCE_TERMS.contains(term)) {
                continue;
            }
            result = replaceOrDelete(result, term.pattern, REPLACEMENTS.get(term));
        }

        return capitalizeSentenceStarts(normalizeWhitespace(result));
    }

    private static String replaceOrDelete(String text, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            if (replacement != null) {
                String matched = matcher.group();
                boolean capitalized = !matched.isEmpty() && Character.isUpperCase(matched.charAt(0));
                result.append(capitalized
                        ? Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1)
                        : replacement);
            }
            // replacement == null: delete outright, append nothing.
            last = matcher.end();
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    /** Paragraph breaks (blank lines) are meaningful — split/rejoin per paragraph so they survive. */
    private static String stripSentencesContaining(String text, Pattern pattern) {
        String[] paragraphs = text.split("\n\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            String[] sentences = paragraphs[p].split("(?<=[.!?])\\s+");
            StringBuilder kept = new StringBuilder();
            for (String sentence : sentences) {
                if (!pattern.matcher(sentence).find()) {
                    if (!kept.isEmpty()) {
                        kept.append(" ");
                    }
                    kept.append(sentence);
                }
            }
            paragraphs[p] = kept.toString();
        }
        return String.join("\n\n", paragraphs);
    }

    private static String normalizeWhitespace(String text) {
        String result = text.replaceAll("[ \\t]{2,}", " ");
        result = result.replaceAll(" +([,.;:!?])", "$1");
        String[] lines = result.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].strip();
        }
        return String.join("\n", lines);
    }

    /** Fixes any lowercase left at a sentence start by a word deletion ("Comprehensive test" -> "test" -> "Test"). */
    private static String capitalizeSentenceStarts(String text) {
        StringBuilder sb = new StringBuilder(text);
        boolean capitalizeNext = true;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (capitalizeNext && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (c == '.' || c == '!' || c == '?') {
                capitalizeNext = true;
            } else if (!Character.isWhitespace(c) && c != '"' && c != '\'') {
                capitalizeNext = false;
            }
        }
        return sb.toString();
    }
}
