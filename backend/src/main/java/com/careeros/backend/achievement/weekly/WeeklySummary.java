package com.careeros.backend.achievement.weekly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

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

    private List<String> highlights;

    private List<String> technologies;

    private Double confidence;

}