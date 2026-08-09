-- The GitHub *account* that authored a commit, as opposed to the git author
-- name/email already stored. Only author_github_id is trustworthy for identity:
-- author_name and author_email are attacker-settable git config values, and one
-- existing row set carries "ranashivang567.com", which is not even an address.
--
-- Nullable with no backfill: existing rows predate the field and cannot be
-- resolved without re-reading every commit from the GitHub API. A null here
-- means "unknown", not "not the owner".

ALTER TABLE github_commits
    ADD COLUMN author_github_id    BIGINT,
    ADD COLUMN author_github_login VARCHAR(255);
