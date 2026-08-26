-- Dismissing an achievement previously only set a flag on the row itself —
-- nothing was learned from it. This records what the dismissed cluster
-- touched (technologies, file paths) so the generator can recognise a new
-- cluster substantially overlapping ground the user has already rejected
-- twice, and skip it before spending an LLM call.

ALTER TABLE achievements
    ADD COLUMN file_paths_json TEXT;

CREATE TABLE dismissed_cluster_signals (

    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    repository_name VARCHAR(255) NOT NULL,

    technologies_json TEXT,

    file_paths_json TEXT,

    dismissed_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_dismissed_cluster_signals_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE

);

CREATE INDEX idx_dismissed_cluster_signals_user_repo
    ON dismissed_cluster_signals (user_id, repository_name);
