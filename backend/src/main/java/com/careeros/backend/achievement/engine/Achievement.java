package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.llm.FlexibleStringListDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Setter-based rather than creator-based on purpose: @Builder.Default only
 * applies through the builder, so with Jackson using an all-args creator a
 * missing key would still arrive as null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {

    private String title;

    private String summary;

    private AchievementType type;

    private String repository;

    // Same exposure as RepositoryKnowledge: model-authored lists that arrive as
    // objects or go missing entirely.
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    private double confidence;

    /** Set when the evidence did not support a grounded claim. Not an error. */
    private boolean insufficient;

    private String reason;
}