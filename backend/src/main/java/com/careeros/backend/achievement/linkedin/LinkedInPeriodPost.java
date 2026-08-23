package com.careeros.backend.achievement.linkedin;

/**
 * No headline — a period post's headline was always a verbatim copy of the
 * post's own first sentence once the "lifted from the post" rule (see
 * LinkedInPromptBuilder) was enforced, which made it pure redundancy rather
 * than a second piece of information. The single-achievement LinkedInPost
 * keeps its headline; this is a genuinely different response shape, not the
 * same one with a field nulled out.
 */
public record LinkedInPeriodPost(String post, Double confidence) {
}
