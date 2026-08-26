package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementTitleSpecificityValidatorTest {

    private final AchievementTitleSpecificityValidator validator = new AchievementTitleSpecificityValidator();

    private static Evidence evidenceWithFiles(String... files) {
        return Evidence.builder().changedFiles(List.of(files)).build();
    }

    @Test
    void rejectsTheExactReportedGamificationTitleEvenAgainstThisCodebasesOwnGithubFiles() {
        // Achievement 36's real title, capital H — reproduced against this
        // codebase's own actual class name (GithubRepositoryService.java,
        // real Java convention: single capital). Turns out these don't
        // collide: "GitHub" camelCase-splits into "Git"+"Hub", both below
        // MIN_TOKEN_LENGTH, so neither survives tokenization — while
        // "GithubRepositoryService" splits into "Github"/"Repository"/
        // "Service", all length >= 4. The self-referential false-positive
        // named in the proposal turns out not to apply to this exact title;
        // see the next test for the case where casing genuinely does collide.
        Evidence evidence = evidenceWithFiles(
                "src/main/java/com/careeros/backend/github/GithubRepositoryService.java");

        var reason = validator.reasonTitleLacksSpecificity("GitHub Committer", evidence);

        assertThat(reason).isPresent();
    }

    @Test
    void aTitleSharingTheSameCasingAsASelfReferentialFilenameStillPasses() {
        // The known, accepted heuristic limit actually described in the
        // class javadoc: a title using the SAME casing as a real filename
        // fragment ("Github", one capital — this codebase's own convention)
        // matches even though "Committer" contributes nothing specific.
        Evidence evidence = evidenceWithFiles(
                "src/main/java/com/careeros/backend/githubcommit/GithubCommitSyncService.java");

        var reason = validator.reasonTitleLacksSpecificity("Github Committer", evidence);

        assertThat(reason).isEmpty();
    }

    @Test
    void rejectsAGamificationTitleAgainstEvidenceItSharesNoWordWith() {
        Evidence evidence = evidenceWithFiles("src/main/java/SpiralSearch.java");

        var reason = validator.reasonTitleLacksSpecificity("GitHub Committer", evidence);

        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("no file, class, method, or technology");
    }

    @Test
    void acceptsATitleNamingAFile() {
        Evidence evidence = evidenceWithFiles("src/main/java/SpiralSearch.java");

        assertThat(validator.reasonTitleLacksSpecificity("Spiral Search Implementation", evidence)).isEmpty();
    }

    @Test
    void acceptsATitleNamingATechnologyEvenWithNoMatchingFile() {
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("main.py"))
                .technologies(List.of("YOLOv8", "easyOCR"))
                .build();

        assertThat(validator.reasonTitleLacksSpecificity("Added YOLOv8 Detection", evidence)).isEmpty();
    }

    @Test
    void doesNotCountCommitMessageProseUnlikeGroundingValidator() {
        // The leak this check exists to close: GroundingValidator's combined
        // vocabulary includes commit-message text, so a title sharing only a
        // prose word (not a file/class/method/tech) still passes there. This
        // check's narrower vocabulary must not have the same leak.
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("src/main/java/SpiralSearch.java"))
                .features(List.of(com.careeros.backend.achievement.extractor.Feature.builder()
                        .name("Feature Development")
                        .evidence(List.of("Synced GitHub repositories for commit history"))
                        .build()))
                .build();

        var reason = validator.reasonTitleLacksSpecificity("GitHub Repository Synchronizer", evidence);

        assertThat(reason).isPresent();
    }

    // The next three tests document a one-direction substring-matching
    // variant that was tried and reverted (see class javadoc): a title
    // token would pass if it were a substring of an evidence token or vice
    // versa. It was rejected because it didn't fix the case it targeted and
    // introduced a worse regression on the case that matters most. Kept as
    // regression tests against exact matching so a future reintroduction of
    // substring matching has to consciously re-break these, not silently.

    @Test
    void relatedWordsThatDoNotShareAnExactTokenAreRejected() {
        // Substring matching ("auth" is a substring of "oauth") would have
        // passed this. Exact matching rejects it — accepted, see class
        // javadoc: an occasional false positive on real, related work is
        // preferred over the regression below.
        Evidence evidence = evidenceWithFiles(
                "src/main/java/com/careeros/backend/github/GithubOAuthSuccessHandler.java");

        assertThat(validator.reasonTitleLacksSpecificity("Auth Token Validation", evidence)).isPresent();
    }

    @Test
    void theRealAchievement35TitleIsRejectedAsAnAcceptedFalsePositive() {
        // "authentication" (14 chars) can't be a substring of "oauth" (5
        // chars — too short to contain it), and "oauth" isn't literally
        // inside "authentication" either. Neither exact matching nor the
        // reverted substring variant passes this real title against its
        // real evidence. Kept rejected deliberately per the "ship option 1"
        // decision — retried once with the failure named, rather than
        // risking the regression below.
        Evidence evidence = evidenceWithFiles(
                "src/main/java/com/careeros/backend/github/GithubOAuthSuccessHandler.java",
                "src/main/java/com/careeros/backend/security/CurrentUserService.java");

        var reason = validator.reasonTitleLacksSpecificity("Refactored Authentication Routes", evidence);

        assertThat(reason).isPresent();
    }

    @Test
    void theSubstringMatchingRegressionThatGotItReverted() {
        // The reason substring matching was reverted: "Committer" literally
        // contains "commit" as a prefix, and "commit" is a common,
        // unrelated evidence token in any repo about processing git commits
        // (CommitCluster.java etc.). Under substring matching this let the
        // exact reported gamification title back through. Exact matching
        // correctly rejects it.
        Evidence evidence = evidenceWithFiles("src/main/java/com/careeros/backend/achievement/cluster/CommitCluster.java");

        var reason = validator.reasonTitleLacksSpecificity("GitHub Committer", evidence);

        assertThat(reason).isPresent();
    }

    @Test
    void aBlankTitleIsRejected() {
        Evidence evidence = evidenceWithFiles("src/main/java/SpiralSearch.java");

        assertThat(validator.reasonTitleLacksSpecificity("", evidence)).isPresent();
        assertThat(validator.reasonTitleLacksSpecificity(null, evidence)).isPresent();
    }

    @Test
    void nullEvidenceIsRejected() {
        assertThat(validator.reasonTitleLacksSpecificity("Spiral Search Implementation", null)).isPresent();
    }
}
