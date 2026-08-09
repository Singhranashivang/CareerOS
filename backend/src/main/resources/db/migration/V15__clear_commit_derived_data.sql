-- INTENTIONAL DATA LOSS. Pre-launch dev database.
--
-- Every github_commits row predates the author filter added in V14, so ~404 of
-- 485 belong to other people's pull requests into Hacktoberfest repositories.
-- syncCommits only ever inserts, so re-syncing cannot remove them.
--
-- A selective delete is not possible: the only stored author signals are
-- author_name and author_email, both git config values the committer chooses.
-- One address in the existing data is "ranashivang567.com", which is not an
-- address at all, and there are 122 distinct emails across 485 rows. Anything
-- derived from those columns would be guesswork.
--
-- So: clear the commit tables and everything generated from them, then re-sync.
-- github_repositories is deliberately kept — the re-sync needs it, and it is not
-- commit-derived.

-- Generated content first. achievements is referenced by
-- scheduled_posts.achievement_id (ON DELETE SET NULL), so it must be DELETEd
-- rather than TRUNCATEd; TRUNCATE refuses on a referenced table without CASCADE.
DELETE FROM linkedin_posts;
DELETE FROM weekly_achievements;
DELETE FROM achievements;

-- Source data last. Nothing references these two, so TRUNCATE is safe.
TRUNCATE TABLE github_pull_requests;
TRUNCATE TABLE github_commits;
