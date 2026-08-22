package com.careeros.backend.achievement.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalOutputLinesTest {

    @Test
    void stripsALogInfoCallButKeepsSurroundingCode() {
        // Unified-diff format: every line starts with +, -, or a space.
        // Shaped after the real diff line that produced "Achieved GitHub
        // Repository Sync".
        String patch = """
                 for (GithubRepository repository : repositories) {
                +    log.info("Syncing pull requests for {}", repository.getFullName());
                     githubPullRequestService.syncPullRequests(
                -            repository, user.getEncryptedGithubAccessToken());
                +            repository, githubTokenEncryptor.decrypt(user.getGithubAccessToken()));
                 }
                """;

        String stripped = OperationalOutputLines.strip(patch);

        assertThat(stripped).doesNotContain("log.info");
        assertThat(stripped).contains("githubPullRequestService.syncPullRequests");
        assertThat(stripped).contains("githubTokenEncryptor.decrypt");
    }

    @Test
    void stripsSystemOutAndSystemErrPrintCalls() {
        String patch = """
                +        System.out.println("Saved PR: " + entity.getTitle());
                +        System.err.print("warning");
                +        int x = 1;
                """;

        String stripped = OperationalOutputLines.strip(patch);

        assertThat(stripped).doesNotContain("System.out").doesNotContain("System.err");
        assertThat(stripped).contains("int x = 1;");
    }

    @Test
    void doesNotStripAReturnedStatusStringOrOtherOrdinaryCode() {
        // Deliberately narrow — see the class javadoc. Only log/print calls
        // are removed; everything else (including the exact line that caused
        // the "Achieved GitHub Repository Sync" achievement) stays, and is
        // instead covered by the prompt's "raw source code, not prose" label.
        String patch = """
                +        return "Synced " + count + " repositories";
                +        throw new RuntimeException("could not sync");
                """;

        String stripped = OperationalOutputLines.strip(patch);

        assertThat(stripped).contains("return \"Synced \" + count + \" repositories\";");
        assertThat(stripped).contains("throw new RuntimeException");
    }

    @Test
    void nullAndBlankPatchesPassThroughUnchanged() {
        assertThat(OperationalOutputLines.strip(null)).isNull();
        assertThat(OperationalOutputLines.strip("")).isEmpty();
    }
}
