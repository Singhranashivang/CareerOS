-- Tracks a single "connect everything" run so the frontend can poll progress
-- instead of blocking on a request that can legitimately take minutes (the
-- analyze step is one-or-two Ollama calls per repository).

CREATE TABLE onboarding_runs (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    stage                   VARCHAR(32) NOT NULL DEFAULT 'CONNECTING',
    repos_found             INT NOT NULL DEFAULT 0,
    commits_synced          INT NOT NULL DEFAULT 0,
    pr_repos_synced         INT NOT NULL DEFAULT 0,
    repos_to_analyze        INT NOT NULL DEFAULT 0,
    repos_analyzed          INT NOT NULL DEFAULT 0,
    achievements_created    INT NOT NULL DEFAULT 0,
    current_repository_name VARCHAR(255),
    error_message           TEXT,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One RUNNING row per user at a time: the start endpoint checks this before
-- creating a new run so a double-click doesn't stack two onboarding jobs.
CREATE UNIQUE INDEX uk_onboarding_one_running_per_user
    ON onboarding_runs (user_id)
    WHERE status = 'RUNNING';
