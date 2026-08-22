package com.careeros.backend.github.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GithubCommitFileResponse {

    private String filename;
    private int additions;
    private int deletions;
    private int changes;

    /** added, modified, removed, renamed */
    private String status;

    /** Unified diff text. GitHub omits this for very large files — null, not absent-and-erroring. */
    private String patch;
}