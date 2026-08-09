package com.careeros.backend.achievement.extractor;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Feature {

    private String name;

    /** Iterated by five prompt builders; never null so none of them guard. */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

}