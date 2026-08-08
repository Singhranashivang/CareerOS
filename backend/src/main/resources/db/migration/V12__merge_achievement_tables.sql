-- achievements and repository_achievements are the same concept split in two:
-- AchievementGeneratorService wrote one, AchievementTimelineService read the
-- other, so engine output never reached the UI. Merging also makes room for
-- non-GitHub sources, where repository_id is simply null.

ALTER TABLE achievements RENAME COLUMN repository TO repository_name;

ALTER TABLE achievements
    ADD COLUMN repository_id   BIGINT REFERENCES github_repositories(id) ON DELETE SET NULL,
    ADD COLUMN source          VARCHAR(32) NOT NULL DEFAULT 'GITHUB',
    ADD COLUMN resume_bullet   TEXT,
    ADD COLUMN star_situation  TEXT,
    ADD COLUMN star_task       TEXT,
    ADD COLUMN star_action     TEXT,
    ADD COLUMN star_result     TEXT;

INSERT INTO achievements (
    user_id, repository_id, repository_name, source, title, resume_bullet,
    star_situation, star_task, star_action, star_result,
    confidence, generated_at
)
SELECT r.user_id, ra.repository_id, r.name, 'GITHUB', ra.title, ra.resume_bullet,
       ra.star_situation, ra.star_task, ra.star_action, ra.star_result,
       ra.confidence, ra.generated_at
FROM repository_achievements ra
         JOIN github_repositories r ON r.id = ra.repository_id;

DROP TABLE repository_achievements;

CREATE INDEX idx_achievements_user_generated
    ON achievements (user_id, generated_at DESC);