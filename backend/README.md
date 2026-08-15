# CareerOS Backend

## Setup

1. Copy the template: `cp src/main/resources/application.properties.example src/main/resources/application.properties`.
   The real file is gitignored — this copy is your local, never-committed config.
2. Set the required environment variables below (the app won't start without them).
3. Run: `./mvnw spring-boot:run`.

## Required environment variables

The app fails to start if either of these is missing — no hardcoded fallback
exists on purpose.

| Variable | Used by | Purpose |
|---|---|---|
| `GITHUB_OAUTH_CLIENT_SECRET` | `spring.security.oauth2.client.registration.github.client-secret` | GitHub OAuth app secret |
| `GITHUB_TOKEN_ENCRYPTION_KEY` | `app.github-token.encryption-key` (`GithubTokenEncryptor`) | Encrypts stored GitHub access tokens at rest |

`spring.security.oauth2.client.registration.github.client-id` stays in
`application.properties` — it's not secret, it's visible in the OAuth
redirect URL itself.

## ⚠️ Rotate the GitHub OAuth client secret

`application.properties` is gitignored now, but it wasn't always: it was
tracked and committed with a plaintext `client-secret` in the initial commit,
then removed from tracking later (`git log -- backend/src/main/resources/application.properties`).
Untracking a file does not remove it from history — anyone who clones this
repo can still `git show` that early commit and read the secret out of it.
Moving the value to `GITHUB_OAUTH_CLIENT_SECRET` here stops *new* commits
from leaking it; it does nothing about the one already sitting in that old
commit.

**Action required:** regenerate the client secret for this app in GitHub
(Settings → Developer settings → OAuth Apps → this app → "Generate a new
client secret"), then set the new value as `GITHUB_OAUTH_CLIENT_SECRET`
everywhere the app runs. Treat whatever secret is currently active on
GitHub's side as compromised until this is done, regardless of whether
history is ever purged.

## Other hardcoded values found in `application.properties`

- `spring.datasource.password=12345` — plaintext, hardcoded, was in the same
  committed-then-untracked history as the OAuth secret. Not moved to an env
  var here: it's a throwaway local Postgres password with no external
  system behind it, so forcing every dev to set one more env var buys
  nothing. Worth revisiting if this ever points at a real database.
- No other credential-shaped values in the file.
- `src/main/resources/application.properties.example` is now committed (see
  Setup above) with every property present. `client-id` is left blank there
  since it's tied to whichever GitHub OAuth App you register, not a secret
  to protect — everything else matches the real file, including keeping
  `spring.datasource.password` as the literal local value above.
