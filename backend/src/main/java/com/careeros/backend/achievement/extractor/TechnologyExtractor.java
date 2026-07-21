package com.careeros.backend.achievement.extractor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TechnologyExtractor {

    private final TechnologyRules technologyRules;

    public List<String> extract(List<String> files) {

        List<String> technologies = new ArrayList<>();

        for (String file : files) {

            String technology = technologyRules.detect(file);

            if (technology != null) {
                technologies.add(technology);
            }

        }

        return technologies.stream()
                .distinct()
                .sorted()
                .toList();
    }

}