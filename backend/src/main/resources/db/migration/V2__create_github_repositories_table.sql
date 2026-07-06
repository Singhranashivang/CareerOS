CREATE TABLE github_repositories (

                                     id BIGSERIAL PRIMARY KEY,

                                     user_id BIGINT NOT NULL REFERENCES users(id),

                                     github_repository_id BIGINT NOT NULL UNIQUE,

                                     name VARCHAR(255) NOT NULL,

                                     full_name VARCHAR(255) NOT NULL,

                                     description TEXT,

                                     language VARCHAR(100),

                                     default_branch VARCHAR(100),

                                     private BOOLEAN NOT NULL,

                                     html_url TEXT,

                                     created_at_github TIMESTAMP,

                                     updated_at_github TIMESTAMP,

                                     synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);