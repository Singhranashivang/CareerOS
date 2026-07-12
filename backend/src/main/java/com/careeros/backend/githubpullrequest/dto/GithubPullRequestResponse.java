package com.careeros.backend.githubpullrequest.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GithubPullRequestResponse {


    private Long id;

    private String title;

    private String body;

    private String state;


    private String htmlUrl;


    private String createdAt;

    private String updatedAt;

    private String mergedAt;


    private User user;


    public boolean isMerged(){
        return mergedAt != null;
    }


    @Getter
    @Setter
    public static class User {

        private String login;

    }
}