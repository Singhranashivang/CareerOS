package com.careeros.backend.achievement.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BannedVocabularyScrubberTest {

    @Test
    void replacesEnhancedPreservingSentenceCaseCapitalization() {
        assertThat(BannedVocabularyScrubber.scrub("I enhanced the system."))
                .isEqualTo("I changed the system.");
        assertThat(BannedVocabularyScrubber.scrub("Enhanced results followed."))
                .isEqualTo("Changed results followed.");
    }

    @Test
    void matchesTheInflectionOfEnhanceAcrossAllFourForms() {
        // The real bug this was added for: "To enhance..." (base form) was
        // substituted with "changed" (past tense) regardless, producing
        // "To changed the system's scalability and maintainability."
        assertThat(BannedVocabularyScrubber.scrub("I want to enhance the system."))
                .isEqualTo("I want to change the system.");
        assertThat(BannedVocabularyScrubber.scrub("I enhanced the system."))
                .isEqualTo("I changed the system.");
        assertThat(BannedVocabularyScrubber.scrub("I am enhancing the system."))
                .isEqualTo("I am changing the system.");
        assertThat(BannedVocabularyScrubber.scrub("This change enhances the system."))
                .isEqualTo("This change changes the system.");
    }

    @Test
    void theExactReportedBugIsFixed() {
        assertThat(BannedVocabularyScrubber.scrub(
                "To enhance the system's scalability and maintainability."))
                .isEqualTo("To change the system's scalability and maintainability.");
    }

    @Test
    void replacesLeveragedAndUtilizedWithUsedMatchingInflection() {
        assertThat(BannedVocabularyScrubber.scrub("I leverage the API."))
                .isEqualTo("I use the API.");
        assertThat(BannedVocabularyScrubber.scrub("I leveraged the API."))
                .isEqualTo("I used the API.");
        assertThat(BannedVocabularyScrubber.scrub("I am leveraging the API."))
                .isEqualTo("I am using the API.");
        assertThat(BannedVocabularyScrubber.scrub("This leverages the API."))
                .isEqualTo("This uses the API.");

        assertThat(BannedVocabularyScrubber.scrub("I utilize the API."))
                .isEqualTo("I use the API.");
        assertThat(BannedVocabularyScrubber.scrub("The script utilized YOLOv8."))
                .isEqualTo("The script used YOLOv8.");
        assertThat(BannedVocabularyScrubber.scrub("The script is utilizing YOLOv8."))
                .isEqualTo("The script is using YOLOv8.");
        assertThat(BannedVocabularyScrubber.scrub("The script utilizes YOLOv8."))
                .isEqualTo("The script uses YOLOv8.");
    }

    @Test
    void replacesStreamlinedWithSimplifiedMatchingInflection() {
        assertThat(BannedVocabularyScrubber.scrub("I streamline the process."))
                .isEqualTo("I simplify the process.");
        assertThat(BannedVocabularyScrubber.scrub("The process was streamlined."))
                .isEqualTo("The process was simplified.");
        assertThat(BannedVocabularyScrubber.scrub("I am streamlining the process."))
                .isEqualTo("I am simplifying the process.");
        assertThat(BannedVocabularyScrubber.scrub("This streamlines the process."))
                .isEqualTo("This simplifies the process.");
    }

    @Test
    void deletesRatherThanSubstitutesWhenTheEdFormIsAdjectival() {
        // "enhanced" here modifies "security" (a noun), it isn't a verb —
        // substituting still produces a verb: "for changed security".
        assertThat(BannedVocabularyScrubber.scrub("Added rate limiting for enhanced security."))
                .isEqualTo("Added rate limiting for security.");
        assertThat(BannedVocabularyScrubber.scrub("This was a leveraged buyout."))
                .isEqualTo("This was a buyout.");
        assertThat(BannedVocabularyScrubber.scrub("Shipped the streamlined onboarding flow."))
                .isEqualTo("Shipped the onboarding flow.");
        // Consonant-starting noun after the deletion: "an" -> "a".
        assertThat(BannedVocabularyScrubber.scrub("This is an enhanced dashboard."))
                .isEqualTo("This is a dashboard.");
    }

    @Test
    void stillSubstitutesTheEdFormWhenItIsActuallyAVerb() {
        // Preceded by a pronoun/subject, not a preposition or article — a
        // real verb, not an adjective, so this must still substitute.
        assertThat(BannedVocabularyScrubber.scrub("The team enhanced the pipeline."))
                .isEqualTo("The team changed the pipeline.");
        assertThat(BannedVocabularyScrubber.scrub("The report was streamlined by the new tool."))
                .isEqualTo("The report was simplified by the new tool.");
    }

    @Test
    void aVerbFormWithNoKnownInflectionStripsTheContainingSentenceInstead() {
        // "enhancement" is a noun, not one of enhance/enhanced/enhancing/
        // enhances — no clean inflected substitution exists, so per the rule
        // the sentence is dropped rather than risk "This was a genuine
        // change" or some other guess.
        String text = "The old version was slow. This was a genuine enhancement.";

        assertThat(BannedVocabularyScrubber.scrub(text)).isEqualTo("The old version was slow.");
    }

    @Test
    void replacesTheTwoMultiWordPhrasesWithNeutralEquivalents() {
        assertThat(BannedVocabularyScrubber.scrub("This prevented unauthorized access to the system."))
                .isEqualTo("This prevented improper access to the system.");
        assertThat(BannedVocabularyScrubber.scrub("The change gave improved reliability."))
                .isEqualTo("The change gave more reliability.");
    }

    @Test
    void deletesAdjectivesAndAdverbsWithNoCleanSubstitution() {
        assertThat(BannedVocabularyScrubber.scrub("The system was robust."))
                .isEqualTo("The system was.");
        assertThat(BannedVocabularyScrubber.scrub("The test suite was comprehensive."))
                .isEqualTo("The test suite was.");
        assertThat(BannedVocabularyScrubber.scrub("The integration was seamless."))
                .isEqualTo("The integration was.");
        assertThat(BannedVocabularyScrubber.scrub("The results were amazing."))
                .isEqualTo("The results were.");
        assertThat(BannedVocabularyScrubber.scrub("This significantly improved performance."))
                .isEqualTo("This improved performance.");
        assertThat(BannedVocabularyScrubber.scrub("This uses cutting-edge technology."))
                .isEqualTo("This uses technology.");
    }

    @Test
    void fixesTheArticleWhenAnAdjectiveDeletionLeavesAnBeforeAConsonant() {
        assertThat(BannedVocabularyScrubber.scrub("This is an amazing tool."))
                .isEqualTo("This is a tool.");
        // Sentence-initial "An" keeps its capital as "A".
        assertThat(BannedVocabularyScrubber.scrub("An amazing feat happened."))
                .isEqualTo("A feat happened.");
        // Untouched when the following word still starts with a vowel sound
        // (spelling-based, not pronunciation-based — see fixArticles).
        assertThat(BannedVocabularyScrubber.scrub("This is an amazing idea."))
                .isEqualTo("This is an idea.");
    }

    @Test
    void cleanTextContainingAFilenameIsReturnedUntouched() {
        // The real bug this was added for: scrub() ran capitalizeSentenceStarts
        // unconditionally, even on text with nothing to substitute, and that
        // pass treats every "." as a sentence end — "README.md" became
        // "README.Md" and "predict_modified.py" became "predict_modified.Py"
        // on achievement rows that had no banned word in them at all.
        String withReadme = "Created initial project setup with README.md, including objectives and tech stack.";
        String withScript = "Created the `predict_modified.py` script that integrates YOLOv8 for object detection.";

        assertThat(BannedVocabularyScrubber.scrub(withReadme)).isEqualTo(withReadme);
        assertThat(BannedVocabularyScrubber.scrub(withScript)).isEqualTo(withScript);
    }

    @Test
    void aFilenameSurvivesUntouchedEvenWhenTheSameTextHasABannedWordElsewhere() {
        String text = "This enhanced README.md with new objectives.";

        assertThat(BannedVocabularyScrubber.scrub(text))
                .isEqualTo("This changed README.md with new objectives.");
    }

    @Test
    void stripsTheWholeSentenceContainingIncredibleJourney() {
        assertThat(BannedVocabularyScrubber.scrub("It's been an incredible journey. I learned a lot."))
                .isEqualTo("I learned a lot.");
    }

    @Test
    void stripsIncredibleJourneySentenceWithoutMergingTheSurroundingParagraphs() {
        String text = "First paragraph is clean.\n\nIt's been an incredible journey.\n\nThird paragraph is clean too.";

        // The gutted paragraph becomes empty rather than disappearing —
        // simpler than re-collapsing paragraph breaks, and harmless: nothing
        // downstream treats an empty paragraph as content.
        assertThat(BannedVocabularyScrubber.scrub(text))
                .isEqualTo("First paragraph is clean.\n\n\n\nThird paragraph is clean too.");
    }

    @Test
    void fixesMultipleDistinctViolationsInOnePass() {
        String text = "I leveraged the plan. This enhanced results and gave improved reliability.";

        assertThat(BannedVocabularyScrubber.scrub(text))
                .isEqualTo("I used the plan. This changed results and gave more reliability.");
    }

    @Test
    void cleanTextIsReturnedUnchanged() {
        String text = "I rerouted two controllers through CurrentUserService.";

        assertThat(BannedVocabularyScrubber.scrub(text)).isEqualTo(text);
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(BannedVocabularyScrubber.scrub(null)).isNull();
        assertThat(BannedVocabularyScrubber.scrub("")).isEmpty();
    }
}
