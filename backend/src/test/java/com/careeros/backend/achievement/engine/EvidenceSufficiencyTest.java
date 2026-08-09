package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceSufficiencyTest {

    private final EvidenceSufficiency sufficiency = new EvidenceSufficiency();

    private static Evidence evidence(int commits, int files, int added, int deleted) {
        return builder(commits, commits, files, added, deleted, List.of()).build();
    }

    private static Evidence.EvidenceBuilder builder(int commits, int sampled, int files,
                                                    int added, int deleted,
                                                    List<String> messages) {
        return builder(commits, sampled, files, added, deleted, messages, 0);
    }

    private static Evidence.EvidenceBuilder builder(int commits, int sampled, int files,
                                                    int added, int deleted,
                                                    List<String> messages,
                                                    long spanMinutes) {
        return Evidence.builder()
                .repositoryName("repo")
                .changedFiles(List.of())
                .features(messages.isEmpty()
                        ? List.of()
                        : List.of(Feature.builder().name("Work").evidence(messages).build()))
                .codeStats(CodeStats.builder()
                        .commitCount(commits)
                        .sampledCommits(sampled)
                        .filesTouched(files)
                        .linesAdded(added)
                        .linesDeleted(deleted)
                        .spanMinutes(spanMinutes)
                        .build());
    }

    // ---- the OR bars ----

    @Test
    void rejectsRepositoryWithNoOwnerCommits() {
        assertThat(sufficiency.shortfall(evidence(0, 0, 0, 0)))
                .hasValueSatisfying(r -> assertThat(r).contains("No commits"));
    }

    @Test
    void acceptsASingleLargeInitialCommit() {
        // One commit, whole project. An AND rule would reject this.
        assertThat(sufficiency.shortfall(evidence(1, 40, 2000, 0))).isEmpty();
    }

    @Test
    void acceptsOnLineChurnAlone() {
        assertThat(sufficiency.shortfall(evidence(1, 1, 90, 20))).isEmpty();
    }

    @Test
    void theCommitBarNowNeedsRealChurnBehindIt() {
        // base-airdrop: 3 commits, 7 changed lines. Used to pass on count alone.
        assertThat(sufficiency.shortfall(evidence(3, 2, 7, 0))).isPresent();
        assertThat(sufficiency.shortfall(evidence(3, 2, 25, 0))).isEmpty();
    }

    @Test
    void fallsBackToChangedFilesWhenStatsApiReturnedNothing() {
        Evidence evidence = Evidence.builder()
                .repositoryName("repo")
                .changedFiles(List.of("a", "b", "c", "d", "e"))
                .codeStats(CodeStats.builder().commitCount(1).sampledCommits(1).build())
                .build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }

    @Test
    void nullEvidenceIsInsufficientRatherThanAnException() {
        assertThat(sufficiency.shortfall(null)).isPresent();
    }

    // ---- veto A: padding by volume ----

    @Test
    void rejectsManyCommitsThatEachChangeAlmostNothing() {
        // contribution-fix: 118 commits, 40 sampled, 91 authored lines.
        Evidence evidence = builder(118, 40, 7, 91, 0, List.of("real work here")).build();

        assertThat(sufficiency.shortfall(evidence))
                .hasValueSatisfying(r -> assertThat(r).contains("commit padding"));
    }

    @Test
    void aRateLimitedStatsCallIsNotMistakenForPadding() {
        // getCommitFileStats returned nothing: zero files, zero lines. Must not
        // read as padding, or an API hiccup rejects a good repository.
        Evidence evidence = builder(50, 40, 0, 0, 0, List.of("a real message")).build();

        assertThat(sufficiency.shortfall(evidence))
                .hasValueSatisfying(r -> assertThat(r).doesNotContain("padding"));
    }

    // ---- veto B: templated messages ----

    @Test
    void rejectsSequentiallyNumberedMessages() {
        List<String> messages = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "commit " + i).map(String.class::cast).toList();

        // Line count deliberately clears a bar; the veto must still fire.
        Evidence evidence = builder(20, 20, 9, 5000, 0, messages).build();

        assertThat(sufficiency.shortfall(evidence))
                .hasValueSatisfying(r -> assertThat(r).contains("sequentially numbered"));
    }

    @Test
    void doesNotFireOnAMajorityOfThreeCommits() {
        // 2 of 3 templated is noise, not evidence.
        Evidence evidence = builder(3, 3, 9, 500, 0,
                List.of("fix contribution 1", "test contribution fix 1", "real work")).build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }

    @Test
    void realMessagesSurviveTheTemplatingCheck() {
        List<String> messages = List.of(
                "Implement GitHub sync and AI achievement pipeline",
                "Add ownership checks to repository-scoped endpoints",
                "feat(schedule): scheduled posts table",
                "Complete backend MVP before frontend",
                "Untrack secrets and IDE config");

        assertThat(sufficiency.shortfall(builder(5, 5, 20, 900, 100, messages).build()))
                .isEmpty();
    }

    // ---- veto C: single scaffolding commit ----

    @Test
    void rejectsASingleScaffoldingCommitHoweverLarge() {
        // mernProjectEcommerce-master: one "first commit", 10,475 authored lines.
        Evidence evidence = builder(1, 1, 119, 10475, 0, List.of("first commit")).build();

        assertThat(sufficiency.shortfall(evidence))
                .hasValueSatisfying(r -> assertThat(r).contains("scaffolding"));
    }

    @Test
    void rejectsASingleCommitWithNoRealMessage() {
        assertThat(sufficiency.shortfall(builder(1, 1, 25, 821, 0, List.of("rtty")).build()))
                .hasValueSatisfying(r -> assertThat(r).contains("scaffolding"));
    }

    @Test
    void rejectsARepositoryWhereNoCommitDescribesAnything() {
        // decentralized-app: three "first commit" commits, 9 minutes apart.
        Evidence evidence = builder(3, 3, 7, 132, 0,
                List.of("first commit", "first commit", "first commit"), 9).build();

        assertThat(sufficiency.shortfall(evidence))
                .hasValueSatisfying(r -> assertThat(r).contains("code dump"));
    }

    @Test
    void iterationOutranksTerseMessages() {
        // React-form: "Initialize project using Create React App" then "Signup",
        // 429 minutes apart, with 375 hand-written lines behind the second one.
        Evidence evidence = builder(2, 2, 11, 746, 0,
                List.of("Initialize project using Create React App", "Signup"), 429).build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }

    @Test
    void aLongSpanDoesNotRescuePaddedCommits() {
        // contribution-fix spans 53 days. The iteration gate must apply only to
        // the scaffolding veto, or padding walks straight through it.
        List<String> messages = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "commit " + i).map(String.class::cast).toList();

        Evidence evidence = builder(118, 40, 7, 90, 0, messages, 77096).build();

        assertThat(sufficiency.shortfall(evidence)).isPresent();
    }

    @Test
    void oneDescriptiveCommitIsEnoughToCountAsAHistory() {
        // Olympic-Analysis: one terse message, one real one.
        Evidence evidence = builder(2, 2, 16, 387, 0,
                List.of("Create README.md", "Initial commit - Olympic Analysis Project")).build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }

    @Test
    void keepsASingleCommitThatActuallyDescribesItself() {
        // "Initial commit: AI Assistant frontend and backend" is descriptive, so
        // it is judged on substance rather than on its message.
        Evidence evidence = builder(1, 1, 17, 4465, 0,
                List.of("Initial commit: full Pexels clone with search and lightbox")).build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }
}
