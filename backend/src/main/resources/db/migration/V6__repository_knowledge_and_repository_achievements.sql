CREATE TABLE repository_knowledge (

                                      id BIGSERIAL PRIMARY KEY,

                                      repository_id BIGINT NOT NULL UNIQUE,

                                      project_type VARCHAR(255),

                                      domain VARCHAR(255),

                                      technologies_json TEXT,

                                      architecture_json TEXT,

                                      features_json TEXT,

                                      developer_contributions_json TEXT,

                                      confidence DOUBLE PRECISION,

                                      generated_at TIMESTAMP,

                                      CONSTRAINT fk_repository_knowledge_repository
                                          FOREIGN KEY (repository_id)
                                              REFERENCES github_repositories(id)
                                              ON DELETE CASCADE

);

CREATE TABLE repository_achievements (

                                         id BIGSERIAL PRIMARY KEY,

                                         repository_id BIGINT NOT NULL,

                                         title VARCHAR(255),

                                         resume_bullet TEXT,

                                         star_situation TEXT,

                                         star_task TEXT,

                                         star_action TEXT,

                                         star_result TEXT,

                                         confidence DOUBLE PRECISION,

                                         generated_at TIMESTAMP,

                                         CONSTRAINT fk_repository_achievement_repository
                                             FOREIGN KEY (repository_id)
                                                 REFERENCES github_repositories(id)
                                                 ON DELETE CASCADE

);