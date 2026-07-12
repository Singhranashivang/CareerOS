CREATE TABLE github_commits (

                                id BIGSERIAL PRIMARY KEY,

                                repository_id BIGINT NOT NULL
                                    REFERENCES github_repositories(id),

                                github_commit_sha VARCHAR(40) NOT NULL UNIQUE,

                                message TEXT NOT NULL,

                                author_name VARCHAR(255),

                                author_email VARCHAR(255),

                                committed_at TIMESTAMP NOT NULL,

                                html_url TEXT,

                                synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);