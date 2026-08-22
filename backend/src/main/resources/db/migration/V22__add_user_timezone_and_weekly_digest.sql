-- The weekly digest fires at 09:00 in each user's own timezone, not the
-- server's. Defaulted to UTC so every existing row stays valid without a
-- backfill, and the scheduler has something to read for users who never set one.
ALTER TABLE users
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

-- One row per user (upserted each run), not a run log — the digest only
-- ever needs "what happened last time", matching last_analyzed_at/
-- analysis_outcome on github_repositories rather than onboarding_runs'
-- append-only history.
CREATE TABLE weekly_digest_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    run_at TIMESTAMP NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    reason TEXT,
    repos_synced INT NOT NULL DEFAULT 0,
    commits_synced INT NOT NULL DEFAULT 0,
    repos_analyzed INT NOT NULL DEFAULT 0,
    achievements_created INT NOT NULL DEFAULT 0
);
