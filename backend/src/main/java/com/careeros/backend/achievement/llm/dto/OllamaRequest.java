package com.careeros.backend.achievement.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OllamaRequest {

    private String model;

    private String prompt;

    private boolean stream;

    private String format;

    /**
     * Without this, Ollama falls back to its own default context window
     * (2048 tokens) regardless of what the model actually supports, and
     * silently truncates anything longer — see LLMService.
     */
    private Options options;

    @Getter
    @Builder
    public static class Options {
        @JsonProperty("num_ctx")
        private int numCtx;
    }
}
