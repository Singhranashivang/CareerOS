ALTER TABLE linkedin_posts
    ADD COLUMN achievement_id BIGINT,
    ADD CONSTRAINT fk_linkedin_achievement
        FOREIGN KEY (achievement_id)
            REFERENCES achievements(id)
            ON DELETE CASCADE;

-- One cached post per achievement; regeneration updates the existing row.
CREATE UNIQUE INDEX uq_linkedin_posts_achievement_id
    ON linkedin_posts (achievement_id);

DELETE FROM linkedin_posts;

ALTER TABLE linkedin_posts
    ALTER COLUMN achievement_id SET NOT NULL;
