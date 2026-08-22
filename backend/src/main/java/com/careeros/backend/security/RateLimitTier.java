package com.careeros.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;

import java.time.Duration;

/**
 * Per-user request budgets, tightened by how expensive the endpoint group
 * actually is downstream — see RateLimitFilter for what maps to which tier.
 */
public enum RateLimitTier {

    /** DB-only reads: dashboard, lists, status polling. Loose — never the thing that makes polling feel broken. */
    READS(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1)))),

    /** Calls the GitHub API, once per repo/commit/PR. Tight enough that a loop can't burn a user's GitHub quota. */
    SYNC(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(5)))),

    /** Calls the local LLM (serialized on one Ollama instance) and/or fetches GitHub evidence. Minutes per call. */
    ANALYZE(Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(10)))),

    /** One LLM call against an already-generated achievement, no GitHub evidence fetch — cheaper than ANALYZE. */
    LINKEDIN_POST(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(10))));

    private final Bandwidth bandwidth;

    RateLimitTier(Bandwidth bandwidth) {
        this.bandwidth = bandwidth;
    }

    public Bandwidth bandwidth() {
        return bandwidth;
    }
}
