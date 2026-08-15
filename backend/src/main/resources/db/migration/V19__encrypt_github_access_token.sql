-- github_access_token has held plaintext tokens despite the (now-renamed)
-- getEncryptedGithubAccessToken accessor. Rather than run bespoke crypto code
-- in a migration that must stay byte-compatible with the runtime
-- TextEncryptor forever, we wipe the existing plaintext and let each user
-- re-authenticate: GithubOAuthSuccessHandler -> UserService now encrypts on
-- the way in via GithubTokenEncryptor. Nullable because a user who hasn't
-- reconnected yet legitimately has no token on file.
ALTER TABLE users
    ALTER COLUMN github_access_token DROP NOT NULL;

UPDATE users
SET github_access_token = NULL;
