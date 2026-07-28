-- Superseded by V6, which contains identical DDL for
-- repository_knowledge and repository_achievements.
-- Both were applied historically on the dev database; on a clean
-- database V5 + V6 both running would fail with "relation already exists".
-- Kept as a no-op so a fresh bootstrap works and Flyway history stays contiguous.
SELECT 1;