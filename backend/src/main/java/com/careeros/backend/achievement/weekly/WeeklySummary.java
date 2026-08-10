package com.careeros.backend.achievement.weekly;

import com.careeros.backend.achievement.llm.FlexibleStringListDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeeklySummary {

    private String title;

    private String summary;

    // Model-authored, same exposure as RepositoryKnowledge. A null here was
    // being serialised into the weekly_achievements row as the string "null".
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    private Double confidence;

    /** Set when the computed confidence fell below the gate. Not persisted. */
    @Builder.Default
    private boolean insufficient = false;

    private String reason;

}