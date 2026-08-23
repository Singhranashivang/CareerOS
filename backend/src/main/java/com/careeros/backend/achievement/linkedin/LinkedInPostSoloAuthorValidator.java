package com.careeros.backend.achievement.linkedin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rejects any output implying more than one person did the work. This app
 * generates achievements from one user's own commit history — a post
 * claiming "a team of five developers" or "collaboration with other
 * developers" is inventing colleagues that appear nowhere in the evidence,
 * the same class of fabrication BannedVocabulary exists to catch, so it
 * gets the same treatment (reject and retry, keep the cleaner attempt).
 */
@Component
public class LinkedInPostSoloAuthorValidator {

    // Stemmed like BannedVocabulary's own list (collaborat\w* catches
    // collaborate/collaborated/collaborating/collaborative too).
    private static final List<Pattern> PATTERNS = List.of(
            "team", "colleagues?", "collaborat\\w*", "code review", "stakeholders?"
    ).stream()
            .map(w -> Pattern.compile("\\b" + w + "\\b", Pattern.CASE_INSENSITIVE))
            .toList();

    private static final Pattern FIRST_PERSON_PLURAL =
            Pattern.compile("\\b(we|us|our|ours|ourselves)\\b", Pattern.CASE_INSENSITIVE);

    /** Distinct offending terms actually present in the text, in the order they occur. */
    public List<String> violationsIn(String text) {
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
        Matcher pronoun = FIRST_PERSON_PLURAL.matcher(text);
        while (pronoun.find()) {
            found.add(pronoun.group());
        }

        return new ArrayList<>(found);
    }
}
