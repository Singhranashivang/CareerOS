CREATE TABLE weekly_achievements (

                                     id BIGSERIAL PRIMARY KEY,

                                     user_id BIGINT NOT NULL,

                                     summary TEXT,

                                     confidence DOUBLE PRECISION,

                                     generated_at TIMESTAMP,

                                     CONSTRAINT fk_weekly_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id)
                                             ON DELETE CASCADE

);