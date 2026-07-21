package com.careeros.backend.achievement.extractor;



public interface TechnologyRule {

    boolean matches(String file);

    String technology();

}
