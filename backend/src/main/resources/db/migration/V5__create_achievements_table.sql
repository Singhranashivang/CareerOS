CREATE TABLE achievements (

                              id BIGSERIAL PRIMARY KEY,

                              user_id BIGINT NOT NULL REFERENCES users(id),

                              title VARCHAR(500),

                              description TEXT,

                              evidence TEXT,

                              confidence DOUBLE PRECISION,

                              status VARCHAR(50),

                              created_at TIMESTAMP NOT NULL

);