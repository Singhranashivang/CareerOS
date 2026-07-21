package com.careeros.backend.github.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GithubContentResponse {

    private String name;

    private String path;

    private String content;

    private String encoding;

}