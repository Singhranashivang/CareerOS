package com.careeros.backend.github.dto;

import lombok.Getter;
import lombok.Setter;

/** Minimal shape from GET /commits/{sha}/pulls — just enough to quote the author's own PR description. */
@Getter
@Setter
public class GithubPullRequestSummary {

    private Long id;

    private String title;

    private String body;
}
