package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.llm.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementBuilder {

    private final AchievementEnginePromptBuilder promptBuilder;
    private final EvidenceSufficiency evidenceSufficiency;
    private final AchievementConfidenceCalculator confidenceCalculator;
    private final AchievementConfidenceGate confidenceGate;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    /**
     * Empty when there is nothing worth claiming — either the evidence never
     * cleared the floor, the model itself said so, or the computed confidence
     * fell below the gate. All normal outcomes, not errors: a repository the
     * user barely touched should produce nothing.
     */
    public List<Achievement> build(Evidence evidence) {

        Optional<String> shortfall = evidenceSufficiency.shortfall(evidence);
        if (shortfall.isPresent()) {
            log.info("Skipping achievement generation for {}: {}",
                    evidence == null ? "unknown repository" : evidence.getRepositoryName(),
                    shortfall.get());
            return List.of();
        }

        try {

            String prompt = promptBuilder.build(evidence);

            String response = llmService.generate(prompt);

            Achievement achievement =
                    objectMapper.readValue(response, Achievement.class);

            if (achievement.isInsufficient()) {
                log.info("Model declined to claim an achievement for {}: {}",
                        evidence.getRepositoryName(), achievement.getReason());
                return List.of();
            }

            // Computed from the evidence, not the model's self-reported number
            // (the prompt asks for one, but every response we've seen just
            // echoes back the example value) — same reasoning as the generator
            // path's AchievementConfidenceCalculator.
            double confidence = confidenceCalculator.calculate(evidence);
            achievement.setConfidence(confidence);

            if (!confidenceGate.passes(confidence)) {
                log.info("Not persisting achievement for {}: {}",
                        evidence.getRepositoryName(), confidenceGate.reasonBelowThreshold(confidence));
                return List.of();
            }

            return List.of(achievement);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to generate achievement",
                    e
            );

        }
    }
}
