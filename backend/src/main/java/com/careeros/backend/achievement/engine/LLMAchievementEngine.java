package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.llm.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class LLMAchievementEngine implements AchievementEngine {

    private final PromptBuilder promptBuilder;
    private final LLMService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AchievementResult generate(AchievementInput input) {

        String prompt = promptBuilder.buildPrompt(input);

        String response = llmService.generate(prompt);

        try {
            return objectMapper.readValue(
                    response,
                    AchievementResult.class
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }
}