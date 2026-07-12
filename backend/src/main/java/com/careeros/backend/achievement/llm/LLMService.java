package com.careeros.backend.achievement.llm;

import com.careeros.backend.achievement.llm.dto.OllamaRequest;
import com.careeros.backend.achievement.llm.dto.OllamaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class LLMService {

    private final RestClient restClient;

    public String generate(String prompt) {

        OllamaRequest request = OllamaRequest.builder()
                .model("qwen3:8b")
                .prompt(prompt)
                .stream(false)
                .format("json")
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