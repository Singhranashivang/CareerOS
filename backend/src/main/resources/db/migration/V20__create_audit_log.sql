CREATE TABLE audit_log (

    id BIGSERIAL PRIMARY KEY,

    occurred_at TIMESTAMP NOT NULL,

    user_id BIGINT NOT NULL,

    action VARCHAR(64) NOT NULL,

    target VARCHAR(255),

    outcome VARCHAR(16) NOT NULL,

    CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE

);

CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);
