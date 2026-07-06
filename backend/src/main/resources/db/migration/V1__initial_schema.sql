                   CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,

                       github_id BIGINT UNIQUE NOT NULL,

                       username VARCHAR(255) NOT NULL,
                       name VARCHAR(255),
                       email VARCHAR(255),
                       avatar_url TEXT,

                       github_access_token TEXT NOT NULL,

                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                   );