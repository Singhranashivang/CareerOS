package com.careeros.backend.achievement.extractor;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FeatureRule {

    private String feature;

    private List<String> keywords;

}