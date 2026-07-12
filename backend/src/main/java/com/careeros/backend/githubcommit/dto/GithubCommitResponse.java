package com.careeros.backend.githubcommit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GithubCommitResponse {

    private String sha;

    private Commit commit;

    @JsonProperty("html_url")
    private String htmlUrl;

    @Data
    public static class Commit {

        private String message;

        private Author author;
    }

    @Data
    public static class Author {

        private String name;

        private String email;

        private String date;
    }
}