-- DELETE /api/account relies on the database to cascade a user's data
-- completely, the same way every other user-owned table already does
-- (achievements, scheduled_posts, onboarding_runs, audit_log, linkedin_posts,
-- weekly_achievements all already have ON DELETE CASCADE from users, and
-- repository_knowledge already cascades from github_repositories). These
-- four foreign keys had no delete action at all (NO ACTION, confirmed against
-- the live schema) — DELETE FROM users would have failed outright the moment
-- a user had any synced repository, commit, pull request, or digest run.

ALTER TABLE github_repositories
    DROP CONSTRAINT github_repositories_user_id_fkey,
    ADD CONSTRAINT github_repositories_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE github_commits
    DROP CONSTRAINT github_commits_repository_id_fkey,
    ADD CONSTRAINT github_commits_repository_id_fkey
        FOREIGN KEY (repository_id) REFERENCES github_repositories (id) ON DELETE CASCADE;

ALTER TABLE github_pull_requests
    DROP CONSTRAINT github_pull_requests_repository_id_fkey,
    ADD CONSTRAINT github_pull_requests_repository_id_fkey
        FOREIGN KEY (repository_id) REFERENCES github_repositories (id) ON DELETE CASCADE;

ALTER TABLE weekly_digest_runs
    DROP CONSTRAINT weekly_digest_runs_user_id_fkey,
    ADD CONSTRAINT weekly_digest_runs_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
