-- github_repository_id was globally UNIQUE, so a repo visible to two users
-- (org repos, forks, collaborations) collided: the second user's sync found
-- the first user's row and reassigned ownership, orphaning their commits,
-- pull requests, knowledge and achievements.
--
-- Identity is (user_id, github_repository_id), not github_repository_id.
--
-- No de-duplication needed: the old global constraint made duplicate
-- (user_id, github_repository_id) pairs impossible to create.

ALTER TABLE github_repositories
DROP CONSTRAINT IF EXISTS github_repositories_github_repository_id_key;

ALTER TABLE github_repositories
    ADD CONSTRAINT uk_repo_user_github_id
        UNIQUE (user_id, github_repository_id);

CREATE INDEX IF NOT EXISTS idx_repo_user_updated
    ON github_repositories (user_id, updated_at_github DESC);