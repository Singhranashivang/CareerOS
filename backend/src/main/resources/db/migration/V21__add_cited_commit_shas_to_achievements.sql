-- Achievements now reason over a commit cluster rather than a whole
-- repository, so cited SHAs must be recorded per achievement — dedup also
-- moves from title equality to this column (a JSON array, always sorted
-- before serializing, so two runs over the same cluster produce an
-- identical string).
ALTER TABLE achievements
    ADD COLUMN cited_commit_shas_json TEXT;
