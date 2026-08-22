package com.careeros.backend.achievement.cluster;

import com.careeros.backend.github.dto.GithubCommitFileResponse;
import com.careeros.backend.githubcommit.GithubCommit;

import java.util.List;
import java.util.Map;

/**
 * A group of a single owner's commits that belong to one piece of work —
 * the achievement engine's unit of reasoning (see {@code CommitClusterer}).
 *
 * {@code filesBySha} is the per-commit changed-file data the clusterer
 * already paid a GitHub API call for while computing path overlap. Evidence
 * building reuses it instead of re-fetching the same commits.
 */
public record CommitCluster(
        List<GithubCommit> commits,
        Map<String, List<GithubCommitFileResponse>> filesBySha
) {
    public int size() {
        return commits.size();
    }
}
