package com.careeros.backend.achievement.star;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StarStory {

    private String title;

    private String situation;

    private String task;

    private String action;

    private String result;

    private Double confidence;

}