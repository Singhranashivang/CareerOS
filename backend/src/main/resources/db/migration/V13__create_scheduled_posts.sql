-- TIMESTAMPTZ throughout: "post Tuesday 9am" means the user's 9am. The instant
-- goes to UTC, user_timezone renders it back. The rest of the schema uses
-- TIMESTAMP/LocalDateTime and loses the offset; that stops here.

CREATE TABLE scheduled_posts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id  BIGINT REFERENCES achievements(id) ON DELETE SET NULL,
    platform        VARCHAR(32)  NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    scheduled_for   TIMESTAMPTZ,
    user_timezone   VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    posted_at       TIMESTAMPTZ,
    external_post_id VARCHAR(255),
    attempt_count   INT          NOT NULL DEFAULT 0,
    failure_reason  TEXT,
    claimed_by      VARCHAR(64),
    claimed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_scheduled_due
    ON scheduled_posts (status, scheduled_for)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_scheduled_user
    ON scheduled_posts (user_id, scheduled_for DESC);
