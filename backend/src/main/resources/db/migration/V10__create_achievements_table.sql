CREATE TABLE achievements (

                              id BIGSERIAL PRIMARY KEY,

                              user_id BIGINT NOT NULL,

                              repository VARCHAR(255),

                              type VARCHAR(255),

                              title VARCHAR(255),

                              summary TEXT,

                              evidence_json TEXT,

                              technologies_json TEXT,

                              confidence DOUBLE PRECISION,

                              generated_at TIMESTAMP,

                              CONSTRAINT fk_achievement_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users(id)
                                      ON DELETE CASCADE
);