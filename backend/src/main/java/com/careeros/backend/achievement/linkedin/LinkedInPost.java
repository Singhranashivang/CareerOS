package com.careeros.backend.achievement.linkedin;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInPost {

    private String headline;

    private String post;

    private Double confidence;

}