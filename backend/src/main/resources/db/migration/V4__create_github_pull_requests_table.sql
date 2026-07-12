CREATE TABLE github_pull_requests (

                                      id BIGSERIAL PRIMARY KEY,

                                      repository_id BIGINT NOT NULL REFERENCES github_repositories(id),

                                      github_pull_request_id BIGINT NOT NULL UNIQUE,

                                      title VARCHAR(500),

                                      body TEXT,

                                      state VARCHAR(50),

                                      html_url TEXT,

                                      author_login VARCHAR(255),

                                      merged BOOLEAN,

                                      created_at_github TIMESTAMP,

                                      updated_at_github TIMESTAMP,

                                      merged_at_github TIMESTAMP,

                                      synced_at TIMESTAMP NOT NULL
);