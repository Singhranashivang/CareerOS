package com.careeros.backend.achievement.knowledge;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryKnowledge {

    private String repositoryName;

    private String projectType;

    private String domain;

    private List<String> technologies;

    private List<String> architecture;

    private List<String> features;

    private List<String> developerContributions;

    private double confidence;

}