CREATE TABLE linkedin_posts (

                                id BIGSERIAL PRIMARY KEY,

                                user_id BIGINT NOT NULL,

                                headline VARCHAR(255),

                                post TEXT,

                                confidence DOUBLE PRECISION,

                                generated_at TIMESTAMP,

                                CONSTRAINT fk_linkedin_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE CASCADE

);