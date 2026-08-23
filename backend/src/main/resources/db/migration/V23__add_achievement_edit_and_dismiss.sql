-- user_edited: set once a human edits the row through PATCH /api/achievements/{id}.
-- The generator only ever inserts new rows (never updates an existing one —
-- exact-SHA dedup blocks a second insert for the same cluster), so this flag
-- is a defensive, checkable signal rather than a guard against an overwrite
-- path that exists today; see AchievementEntity.
--
-- dismissed: set by POST /api/achievements/{id}/dismiss. Excluded from the
-- default achievement list and digest, and the generator skips any cluster
-- whose commit set matches a dismissed achievement rather than regenerating it.
ALTER TABLE achievements
    ADD COLUMN user_edited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dismissed BOOLEAN NOT NULL DEFAULT FALSE;
