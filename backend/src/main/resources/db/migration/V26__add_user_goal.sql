-- Null until asked during onboarding — no default, matching how User.timezone/goal null-handling works elsewhere.
ALTER TABLE users ADD COLUMN goal VARCHAR(32);
