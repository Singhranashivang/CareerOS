ALTER TABLE weekly_achievements
    ADD COLUMN title VARCHAR(255);

ALTER TABLE weekly_achievements
    ADD COLUMN highlights_json TEXT;

ALTER TABLE weekly_achievements
    ADD COLUMN technologies_json TEXT;