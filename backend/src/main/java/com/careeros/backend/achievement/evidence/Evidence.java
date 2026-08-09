package com.careeros.backend.achievement.evidence;

import com.careeros.backend.achievement.extractor.Feature;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {

    private String repositoryName;

    private String description;

    private String language;

    private String readme;

    private CodeStats codeStats;

    // Defaulted so a partially built Evidence cannot NPE a prompt builder.
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    @Builder.Default
    private List<Feature> features = new ArrayList<>();

    @Builder.Default
    private List<String> pullRequestTitles = new ArrayList<>();

    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @Builder.Default
    private List<String> repositoryTree = new ArrayList<>();

    @Builder.Default
    private List<String> changedFiles = new ArrayList<>();

    @Builder.Default
    private List<String> repositoryFeatures = new ArrayList<>();

    @Builder.Default
    private List<String> changedFileInsights = new ArrayList<>();


}