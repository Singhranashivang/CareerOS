package com.careeros.backend.achievement.extractor;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Feature {

    private String name;

    private List<String> evidence;

}