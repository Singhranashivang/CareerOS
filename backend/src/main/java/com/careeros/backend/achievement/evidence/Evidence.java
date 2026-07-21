package com.careeros.backend.achievement.evidence;

import com.careeros.backend.achievement.extractor.Feature;
import lombok.*;

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

    private List<String> dependencies;

    private List<Feature> features;

    private List<String> pullRequestTitles;

    private List<String> technologies;

    private List<String> repositoryTree;

    private List<String> changedFiles;

    private List<String> repositoryFeatures;

    private List<String> changedFileInsights;


}