# Who Lived When

Spring Boot 4 + Kotlin backend (`backend/`), Next.js 16 frontend (`frontend/`, see `frontend/CLAUDE.md`). Product and design context: `README.md`.

## Local commands

- Java 25 (jenv, `.java-version`) and Node 24 (nvm, `.nvmrc`). The shell's default Node is older: run `source ~/.nvm/nvm.sh && nvm use` in `frontend/` before any npm command.
- Backend: `cd backend && ./gradlew test` / `./gradlew bootRun` (port 8080).
- Frontend: `cd frontend && npm run lint && npm run build` / `npm run dev` (port 3000).
- API tests: with the backend running (`--app.wikipedia.enrich=false`), `ijhttp --env-file http/http-client.env.json --env local http/api.http` (add `-V baseUrl=http://localhost:<port>` for another port). Never point them at production.
- Both in Docker: `docker compose up --build`, then http://localhost:3000. Docker Desktop is often not running on this Mac.
- End-to-end (Playwright, against a running site): `cd e2e && npm ci && npm run typecheck && npx playwright test` (`BASE_URL`, default http://localhost:3000). Locally it drives the installed Google Chrome: Playwright's Chromium does not run on macOS 13.

## Commits

Public repo: https://github.com/charlotte-hib/who-lived-when

- Conventional Commits, small and logical, with a body explaining why. Keep the tree clean.
- Author `charlotte-hib <9551327+charlotte-hib@users.noreply.github.com>`. No `Co-Authored-By` trailer.
- `main` is protected by the "Protect main" ruleset: no direct push, force push or deletion. Work on a branch and open a pull request; it merges (squash or rebase) once "Backend tests" and "Frontend lint and build" pass. Merging deploys to production.

## Production

Merging to `main` deploys. Server details and access are kept out of this public repo: @~/.claude/who-lived-when-ops.md

- **Pipeline** (`.github/workflows/ci.yml`): pull requests run backend tests and frontend lint and build. On `main`, images are built once, pushed to GHCR (`ghcr.io/charlotte-hib/who-lived-when-{backend,frontend}`, tagged `sha-<commit>`) with signed SLSA provenance and SPDX SBOM attestations. The `deploy` job (environment `production`, `main` only) verifies the provenance, joins the tailnet through Tailscale workload identity federation, and runs `deploy/deploy.sh` over Tailscale SSH. `workflow_dispatch` redeploys `main` by hand.
- **No secrets in GitHub.** Repository variables only. Do not add SSH keys or tokens.
- **`deploy/deploy.sh`** runs on the server in a clone of this repo: checks out the commit, writes the release (commit and image digests) to `.env`, `docker compose up`, probes `/` and `/api/moments` through the reverse proxy, and rolls back to `.env.previous` if unhealthy within 3 minutes. Its body is in functions because it rewrites itself through `git checkout`.
- **Compose**: `backend` (no published port, `SPRING_PROFILES_ACTIVE=prod` disables the H2 console) and `frontend` on `127.0.0.1:3000`. No volumes: H2 is in memory and reseeds on every start; Wikipedia enrichment takes about a minute.
- Keep server details (addresses, hostnames, users, firewall, other sites) and personal details out of tracked files and commit messages, including `README.md` and this file. The server provides the site addresses: its Caddyfile lists them, and `site.env` (untracked, next to `.env`) gives `deploy.sh` the address to probe.
