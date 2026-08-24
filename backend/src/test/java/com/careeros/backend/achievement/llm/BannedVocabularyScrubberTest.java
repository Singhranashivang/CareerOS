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
    void replacesLeveragedAndUtilizedWithUsed() {
        assertThat(BannedVocabularyScrubber.scrub("I leveraged the API."))
                .isEqualTo("I used the API.");
        assertThat(BannedVocabularyScrubber.scrub("The script utilized YOLOv8."))
                .isEqualTo("The script used YOLOv8.");
    }

    @Test
    void replacesStreamlinedWithSimplified() {
        assertThat(BannedVocabularyScrubber.scrub("The process was streamlined."))
                .isEqualTo("The process was simplified.");
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
    void deletingAnAdjectiveRightAfterAnArticleCanLeaveAGrammarArtifact() {
        // ponytail: known ceiling of word-level deletion — "an" isn't
        // re-agreed to "a" (or dropped) once the noun after the deleted
        // adjective no longer needs it. Deterministic and always
        // banned-word-free; not grammar-perfect. Upgrade path: track the
        // preceding article token and drop it too when it no longer agrees.
        assertThat(BannedVocabularyScrubber.scrub("This is an amazing tool."))
                .isEqualTo("This is an tool.");
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
