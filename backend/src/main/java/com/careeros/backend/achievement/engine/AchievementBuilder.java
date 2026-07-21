package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.llm.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AchievementBuilder {

    private final AchievementEnginePromptBuilder promptBuilder;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public List<Achievement> build(Evidence evidence) {

        try {

            String prompt = promptBuilder.build(evidence);

            System.out.println("\n========== ACHIEVEMENT PROMPT ==========");
            System.out.println(prompt);

            String response = llmService.generate(prompt);

            System.out.println("\n========== ACHIEVEMENT RESPONSE ==========");
            System.out.println(response);

            Achievement achievement =
                    objectMapper.readValue(
                            response,
                            Achievement.class
                    );

            return List.of(achievement);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to generate achievement",
                    e
            );

        }
    }
}