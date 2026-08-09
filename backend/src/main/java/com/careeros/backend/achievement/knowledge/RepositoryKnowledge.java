package com.careeros.backend.achievement.knowledge;

import com.careeros.backend.achievement.llm.FlexibleStringListDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Every field here is model-authored, so nothing about the incoming JSON is
 * guaranteed: keys get renamed, lists arrive as objects, keys go missing.
 *
 * Three defences, because relying on any one of them has already failed:
 *  - @JsonProperty pins the expected key instead of trusting bean naming.
 *  - @JsonAlias absorbs the renames actually observed from the model.
 *  - @Builder.Default guarantees a list is never null, so a key the model
 *    simply omits cannot NPE a prompt builder three classes downstream.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryKnowledge {

    @JsonProperty("repositoryName")
    @JsonAlias({"repository_name", "repo_name", "name"})
    private String repositoryName;

    @JsonProperty("projectType")
    @JsonAlias({"project_type", "type"})
    private String projectType;

    @JsonProperty("domain")
    @JsonAlias({"business_domain", "businessDomain"})
    private String domain;

    @JsonProperty("technologies")
    @JsonAlias({"technology", "tech_stack", "techStack", "technologies_used"})
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @JsonProperty("architecture")
    @JsonAlias({"architecture_layers", "architectureLayers", "layers"})
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> architecture = new ArrayList<>();

    /** The model has returned this as "major_features"; hence the aliases. */
    @JsonProperty("features")
    @JsonAlias({"major_features", "majorFeatures", "repository_features",
                "repositoryFeatures", "key_features", "keyFeatures"})
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> features = new ArrayList<>();

    /** Seen as "developer_contributions", and as an object of boolean flags. */
    @JsonProperty("developerContributions")
    @JsonAlias({"developer_contributions", "contributions", "developerWork"})
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    @Builder.Default
    private List<String> developerContributions = new ArrayList<>();

    @JsonProperty("confidence")
    private double confidence;
}
