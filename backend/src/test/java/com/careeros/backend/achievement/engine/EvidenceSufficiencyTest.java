package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceSufficiencyTest {

    private final EvidenceSufficiency sufficiency = new EvidenceSufficiency();

    private static Evidence evidence(int commits, int files, int added, int deleted) {
        return Evidence.builder()
                .repositoryName("repo")
                .changedFiles(List.of())
                .codeStats(CodeStats.builder()
                        .commitCount(commits)
                        .filesTouched(files)
                        .linesAdded(added)
                        .linesDeleted(deleted)
                        .build())
                .build();
    }

    @Test
    void rejectsRepositoryWithNoOwnerCommits() {
        assertThat(sufficiency.shortfall(evidence(0, 0, 0, 0)))
                .hasValueSatisfying(r -> assertThat(r).contains("No commits"));
    }

    @Test
    void rejectsTheHacktoberfestResidueShape() {
        // one "Add files via upload" touching a couple of files
        assertThat(sufficiency.shortfall(evidence(1, 2, 30, 4))).isPresent();
    }

    @Test
    void acceptsASingleLargeInitialCommit() {
        // The AI-Assistant shape: one commit, whole project. An AND rule would
        // reject this, which is the reason the rule is an OR.
        assertThat(sufficiency.shortfall(evidence(1, 40, 2000, 0))).isEmpty();
    }

    @Test
    void acceptsSeveralSmallCommits() {
        assertThat(sufficiency.shortfall(evidence(3, 1, 10, 2))).isEmpty();
    }

    @Test
    void acceptsOnLineChurnAlone() {
        assertThat(sufficiency.shortfall(evidence(1, 1, 90, 20))).isEmpty();
    }

    @Test
    void fallsBackToChangedFilesWhenStatsApiReturnedNothing() {
        // getCommitFileStats failed (zeroes) but getCommitFiles answered.
        Evidence evidence = Evidence.builder()
                .repositoryName("repo")
                .changedFiles(List.of("a", "b", "c", "d", "e"))
                .codeStats(CodeStats.builder().commitCount(1).build())
                .build();

        assertThat(sufficiency.shortfall(evidence)).isEmpty();
    }

    @Test
    void nullEvidenceIsInsufficientRatherThanAnException() {
        assertThat(sufficiency.shortfall(null)).isPresent();
    }
}
