package com.careeros.backend.achievement.linkedin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedInPostSoloAuthorValidatorTest {

    private final LinkedInPostSoloAuthorValidator validator = new LinkedInPostSoloAuthorValidator();

    @Test
    void catchesTeamAndAPluralFirstPersonPronoun() {
        // The real bug this was added for: a solo dev's post invented
        // "a team of five developers" and "collaboration with other developers".
        var violations = validator.violationsIn("Our team of five developers shipped this.");

        assertThat(violations).contains("Our", "team");
    }

    @Test
    void catchesCollaborationInAnyStemmedForm() {
        assertThat(validator.violationsIn("Close collaboration made this possible.")).contains("collaboration");
        assertThat(validator.violationsIn("We collaborated on the fix.")).contains("collaborated");
        assertThat(validator.violationsIn("A collaborative effort.")).contains("collaborative");
    }

    @Test
    void catchesColleaguesCodeReviewAndStakeholders() {
        assertThat(validator.violationsIn("My colleagues helped review it.")).contains("colleagues");
        assertThat(validator.violationsIn("It passed code review.")).contains("code review");
        assertThat(validator.violationsIn("Stakeholders were pleased.")).contains("Stakeholders");
    }

    @Test
    void catchesEachFirstPersonPluralPronoun() {
        assertThat(validator.violationsIn("We built it.")).contains("We");
        assertThat(validator.violationsIn("It was us.")).contains("us");
        assertThat(validator.violationsIn("It was our idea.")).contains("our");
        assertThat(validator.violationsIn("The credit is ours.")).contains("ours");
        assertThat(validator.violationsIn("We did it ourselves.")).contains("ourselves");
    }

    @Test
    void aCleanSoloFirstPersonPostHasNoViolations() {
        String post = "I refactored the auth routes through CurrentUserService. "
                + "I removed the duplicated logic myself.";

        assertThat(validator.violationsIn(post)).isEmpty();
    }

    @Test
    void aBlankOrNullTextHasNoViolations() {
        assertThat(validator.violationsIn("")).isEmpty();
        assertThat(validator.violationsIn(null)).isEmpty();
    }

    @Test
    void doesNotFalsePositiveOnSubstringsOfTheBannedWords() {
        // "team" must not match inside unrelated words like "steam" or "esteemed".
        assertThat(validator.violationsIn("I let off some steam after shipping.")).isEmpty();
        assertThat(validator.violationsIn("An esteemed reviewer once said so.")).isEmpty();
    }
}
