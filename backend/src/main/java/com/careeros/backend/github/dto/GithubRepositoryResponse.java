package com.careeros.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GithubRepositoryResponse {

    private Long id;

    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private String description;

    private String language;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("private")
    private Boolean privateRepo;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}