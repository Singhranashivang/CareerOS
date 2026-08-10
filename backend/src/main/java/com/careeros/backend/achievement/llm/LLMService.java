package com.careeros.backend.achievement.llm;

import com.careeros.backend.achievement.llm.dto.OllamaRequest;
import com.careeros.backend.achievement.llm.dto.OllamaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class LLMService {

    private final RestClient restClient;

    @Value("${app.llm.model}")
    private String model;

    /**
     * Ollama's own default (2048) when this isn't sent — regardless of what
     * the model itself supports (qwen2.5-coder:7b goes up to 32768). A large
     * repo's prompt silently exceeding 2048 was exactly what caused CareerOS's
     * schema-drift ERRORs: Ollama truncates from the front of the prompt to
     * fit the window, which was cutting the JSON schema instruction (it lived
     * near the top) while leaving the evidence dump at the tail intact — the
     * model then improvised its own JSON shape because it never saw ours.
     */
    @Value("${app.llm.num-ctx:8192}")
    private int numCtx;

    public String generate(String prompt) {

        OllamaRequest request = OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .format("json")
                .options(OllamaRequest.Options.builder().numCtx(numCtx).build())
                .build();

        OllamaResponse response = restClient.post()
                .uri("http://localhost:11434/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null) {
            throw new RuntimeException("No response received from Ollama");
        }

        System.out.println("========== OLLAMA RESPONSE ==========");
        System.out.println(response.getResponse());
        System.out.println("=====================================");

        return response.getResponse();
    }
}