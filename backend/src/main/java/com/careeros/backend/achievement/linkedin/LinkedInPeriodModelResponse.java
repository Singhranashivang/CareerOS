package com.careeros.backend.achievement.linkedin;

import java.util.List;

/**
 * Raw shape the period prompt asks the model for — paragraphs as a JSON
 * array, not one string with "\n\n" embedded in it. A model that must emit
 * N array elements is structurally more likely to actually produce N
 * paragraphs than one asked to insert blank lines into prose was: that
 * instruction alone left 7/10 first attempts as a single unbroken block.
 * LinkedInPostService joins the elements with "\n\n" into a LinkedInPost
 * before anything downstream (validators, scrubbing, persistence) sees it —
 * this shape doesn't leak past parsing.
 */
public record LinkedInPeriodModelResponse(List<String> paragraphs, Double confidence) {
}
