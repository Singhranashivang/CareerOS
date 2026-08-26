package com.careeros.backend.achievement.llm;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic cleanup for whatever BannedVocabulary.violationsIn still
 * finds after generation — see that class's javadoc for why this replaced
 * asking the model to avoid the words itself. Every term gets one of four
 * treatments:
 *
 * - an inflection-matched verb substitution (VERB_REPLACEMENTS) — "enhance"
 *   becomes "change", "enhanced" becomes "changed", "enhancing" becomes
 *   "changing", never a tense mismatch like "To changed the system's
 *   scalability" (a real generated post did exactly that before this
 *   existed: matching only the stem and always substituting one fixed word).
 *   The "ed" form is deleted instead of substituted when it's adjectival
 *   ("for enhanced security" -> "for security", not "for changed security")
 *   — see isAdjectivalPosition;
 * - a fixed neutral phrase (PHRASE_REPLACEMENTS) for the two multi-word
 *   terms, which aren't verbs and so have no inflection to match;
 * - deleted outright, for adjectives/adverbs where removing the word leaves
 *   a grammatical sentence ("a robust test" -> "a test") — see fixArticles
 *   for the one systematic side effect this can leave behind;
 * - the whole sentence it's in removed (STRIP_SENTENCE_TERMS), for a filler
 *   phrase that isn't a modifier ("It's been an incredible journey." ->
 *   "It's been an.") or a verb-stem match whose suffix isn't one of the
 *   four known inflections (e.g. "enhancement" — a noun, not a form of
 *   "enhance" this class knows how to substitute).
 */
public final class BannedVocabularyScrubber {

    private record VerbForms(String base, String past, String gerund, String thirdPerson) {
    }

    private static final Map<BannedVocabulary.Term, VerbForms> VERB_REPLACEMENTS = Map.of(
            BannedVocabulary.Term.ENHANCED, new VerbForms("change", "changed", "changing", "changes"),
            BannedVocabulary.Term.LEVERAGED, new VerbForms("use", "used", "using", "uses"),
            BannedVocabulary.Term.UTILIZED, new VerbForms("use", "used", "using", "uses"),
            BannedVocabulary.Term.STREAMLINED, new VerbForms("simplify", "simplified", "simplifying", "simplifies")
    );

    private static final Map<BannedVocabulary.Term, String> PHRASE_REPLACEMENTS = Map.of(
            BannedVocabulary.Term.UNAUTHORIZED_ACCESS, "improper access",
            BannedVocabulary.Term.IMPROVED_RELIABILITY, "more reliability"
    );

    // ponytail: sentence-level removal, not the finer comma-clause removal a
    // human editor might do — simpler and never leaves a broken fragment.
    private static final Set<BannedVocabulary.Term> STRIP_SENTENCE_TERMS =
            Set.of(BannedVocabulary.Term.INCREDIBLE_JOURNEY);

    private static final Pattern AN_BEFORE_CONSONANT = Pattern.compile("\\b[Aa]n(?=\\s+[^aeiouAEIOU\\s])");

    /**
     * A verb-stem term's past-participle form ("enhanced", "leveraged", ...)
     * directly after one of these is an adjective modifying the noun that
     * follows ("for enhanced security"), not a verb — substituting still
     * produces a verb where a noun phrase belongs ("for changed security").
     * Restricted to the "ed" suffix: it's the only one of the four
     * inflections English uses pre-nominally this way ("to enhance" after
     * "to" is an infinitive verb, not this case — see isAdjectivalPosition).
     */
    private static final Set<String> ADJECTIVAL_PRECEDERS = Set.of(
            "a", "an", "the",
            "for", "with", "by", "of", "in", "on", "at", "from", "into", "onto", "upon",
            "about", "after", "before", "during", "without", "within", "through", "via", "as", "per", "over", "under");

    private static final Pattern TRAILING_WORD = Pattern.compile("([A-Za-z']+)\\s*$");
    private static final Pattern LEADING_WORD = Pattern.compile("^\\s*[A-Za-z]");

    private BannedVocabularyScrubber() {
    }

    public static String scrub(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // A true no-op on clean text, not just "nothing left to substitute" —
        // the cleanup passes below (capitalizeSentenceStarts especially, which
        // treats every "." as a sentence end) are only safe to run on text
        // they've actually modified. Without this guard, calling scrub() on
        // text with no banned words still ran that pass and corrupted names
        // with periods in them — "README.md" became "README.Md",
        // "predict_modified.py" became "predict_modified.Py" — real damage
        // a backfill run did to achievement rows that had nothing to scrub.
        if (BannedVocabulary.violationsIn(text).isEmpty()) {
            return text;
        }

        String result = text;
        for (BannedVocabulary.Term term : STRIP_SENTENCE_TERMS) {
            result = stripSentencesMatching(result, sentence -> term.pattern.matcher(sentence).find());
        }
        for (var entry : VERB_REPLACEMENTS.entrySet()) {
            result = applyVerbReplacement(result, entry.getKey(), entry.getValue());
        }
        for (BannedVocabulary.Term term : BannedVocabulary.Term.values()) {
            if (STRIP_SENTENCE_TERMS.contains(term) || VERB_REPLACEMENTS.containsKey(term)) {
                continue;
            }
            result = replaceOrDelete(result, term.pattern, PHRASE_REPLACEMENTS.get(term));
        }

        return fixArticles(capitalizeSentenceStarts(normalizeWhitespace(result)));
    }

    /**
     * A match's suffix beyond the term's stem tells us which of the four
     * forms to substitute. "enhancement"/"enhancements" (stem + "ement(s)")
     * fall through to null — not a verb form this class can confidently
     * inflect, so the caller strips that sentence instead of guessing.
     */
    private static String inflectedFormOf(VerbForms forms, String matched, String stem) {
        String suffix = matched.substring(stem.length()).toLowerCase();
        return switch (suffix) {
            case "e" -> forms.base();
            case "ed" -> forms.past();
            case "ing" -> forms.gerund();
            case "es" -> forms.thirdPerson();
            default -> null;
        };
    }

    private static String applyVerbReplacement(String text, BannedVocabulary.Term term, VerbForms forms) {

        // A sentence containing a form we can't confidently inflect gets
        // dropped whole, same as INCREDIBLE_JOURNEY — better than leaving
        // "enhancement" untouched (still a banned word) or guessing wrong.
        String cleaned = stripSentencesMatching(text, sentence -> {
            Matcher m = term.pattern.matcher(sentence);
            while (m.find()) {
                if (inflectedFormOf(forms, m.group(), term.stem) == null) {
                    return true;
                }
            }
            return false;
        });

        Matcher matcher = term.pattern.matcher(cleaned);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(cleaned, last, matcher.start());
            String matched = matcher.group();
            String suffix = matched.substring(term.stem.length()).toLowerCase();
            if (!suffix.equals("ed") || !isAdjectivalPosition(cleaned, matcher.start(), matcher.end())) {
                // Never null here — matches with no known inflection were already stripped above.
                String replacement = inflectedFormOf(forms, matched, term.stem);
                result.append(Character.isUpperCase(matched.charAt(0))
                        ? Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1)
                        : replacement);
            }
            // Adjectival "ed": delete outright, same as the DELETE-type
            // terms — "for enhanced security" -> "for security", not
            // "for changed security". fixArticles below cleans up any
            // "an" this strands before a consonant.
            last = matcher.end();
        }
        result.append(cleaned, last, cleaned.length());
        return result.toString();
    }

    /**
     * True when the match at [start, end) is preceded by an article or
     * preposition and followed by another word — the shape a pre-nominal
     * adjective takes ("for enhanced security", "a leveraged buyout"). A
     * bare "to" + base form ("to enhance") never hits this: base form's
     * suffix is "e", not "ed", so the caller only calls this for "ed".
     */
    private static boolean isAdjectivalPosition(String text, int start, int end) {
        Matcher before = TRAILING_WORD.matcher(text.substring(0, start));
        if (!before.find() || !ADJECTIVAL_PRECEDERS.contains(before.group(1).toLowerCase())) {
            return false;
        }
        return LEADING_WORD.matcher(text.substring(end)).find();
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
    private static String stripSentencesMatching(String text, Predicate<String> shouldDrop) {
        String[] paragraphs = text.split("\n\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            String[] sentences = paragraphs[p].split("(?<=[.!?])\\s+");
            StringBuilder kept = new StringBuilder();
            for (String sentence : sentences) {
                if (!shouldDrop.test(sentence)) {
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

    /**
     * Fixes any lowercase left at a sentence start by a word deletion
     * ("Comprehensive test" -> "test" -> "Test"). A "." only counts as a
     * sentence end once it's followed by whitespace — a bare "." with a
     * letter right after it, no space, is a filename or identifier
     * ("README.md", "predict_modified.py"), not a new sentence. Without
     * that distinction this corrupted real achievement text: "README.md"
     * became "README.Md".
     */
    private static String capitalizeSentenceStarts(String text) {
        StringBuilder sb = new StringBuilder(text);
        boolean afterSentenceEndingPunctuation = false;
        boolean capitalizeNext = true;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (capitalizeNext && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c));
                capitalizeNext = false;
                afterSentenceEndingPunctuation = false;
            } else if (c == '.' || c == '!' || c == '?') {
                afterSentenceEndingPunctuation = true;
            } else if (Character.isWhitespace(c)) {
                if (afterSentenceEndingPunctuation) {
                    capitalizeNext = true;
                }
            } else if (c != '"' && c != '\'') {
                capitalizeNext = false;
                afterSentenceEndingPunctuation = false;
            }
        }
        return sb.toString();
    }

    /**
     * Fixes "an" left stranded before a now-consonant-starting word by an
     * adjective deletion ("an amazing tool" -> "an tool" -> "a tool").
     * Spelling-based, not pronunciation-based — a deletion that leaves "an"
     * before a word like "SQL" or "8-hour" (consonant letter, vowel sound)
     * still reads wrong. Only DELETE-type terms (robust, comprehensive,
     * cutting-edge, amazing, seamless, significantly) can produce this;
     * VERB_REPLACEMENTS and PHRASE_REPLACEMENTS always substitute something
     * where the word to attach an article to isn't the fix target.
     */
    private static String fixArticles(String text) {
        return AN_BEFORE_CONSONANT.matcher(text).replaceAll(
                mr -> Character.isUpperCase(mr.group().charAt(0)) ? "A" : "a");
    }
}
