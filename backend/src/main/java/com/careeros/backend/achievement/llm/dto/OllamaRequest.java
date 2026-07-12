package com.careeros.backend.achievement.llm.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OllamaRequest {

    private String model;

    private String prompt;

    private boolean stream;

    private String format;

}