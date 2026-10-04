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

- **Pipeline** (`.github/workflows/ci.yml`): pull requests run backend tests and frontend lint and build, then the `images` job builds each image once (a one-day artifact) and the API tests (`http/`) and end-to-end tests (`e2e/`) load those exact images; only the first two are required checks. On `main`, the same images are pushed to GHCR (`ghcr.io/charlotte-hib/who-lived-when-{backend,frontend}`, tagged `sha-<commit>`) with signed SLSA provenance and SPDX SBOM attestations. The `deploy` job (environment `production`, `main` only) waits for every test, verifies the provenance, joins the tailnet through Tailscale workload identity federation, and runs `deploy/deploy.sh` over Tailscale SSH. `workflow_dispatch` redeploys `main` by hand.
- **No secrets in GitHub.** Repository variables only. Do not add SSH keys or tokens.
- **`deploy/deploy.sh`** runs on the server in a clone of this repo: checks out the commit, writes the release (commit and image digests) to `.env`, `docker compose up` for `backend` and `frontend`, probes `/` and `/api/moments` through the reverse proxy, and rolls back to `.env.previous` if unhealthy within 3 minutes. Once healthy, it starts or updates Prometheus and Grafana when `grafana.env` exists (adding `COMPOSE_PROFILES=monitoring` to `.env`); a monitoring failure never fails the deploy or rolls it back. Its body is in functions because it rewrites itself through `git checkout`.
- **Compose**: `backend` (no published port, `SPRING_PROFILES_ACTIVE=prod` disables the H2 console and moves Actuator, `/actuator/health` and `/actuator/prometheus`, to port 8081, reachable on the Compose network only) and `frontend` on `127.0.0.1:3000`. H2 is in memory and reseeds on every start; Wikipedia enrichment takes about a minute.
- **Monitoring** (`monitoring/`, Compose profile `monitoring`, off in CI and by default): `prometheus` (30 days or 1 GB) and `grafana` on `127.0.0.1:3001` only, never in the Caddy snippet, with named volumes `prometheus-data` and `grafana-data`. Grafana's admin password comes from `grafana.env` (`GF_SECURITY_ADMIN_PASSWORD=...`, untracked, next to `.env`). Datasource and dashboards are provisioned from the repo: change the JSON in `monitoring/grafana/dashboards/`, not in the UI. Visitor events (`POST /api/events`, `frontend/lib/analytics.ts`, `VisitorEvents.kt`) carry only labels from closed sets: no ids, IPs, user agents or URLs.
- Keep server details (addresses, hostnames, users, firewall, other sites) and personal details out of tracked files and commit messages, including `README.md` and this file. The server provides the site addresses: its Caddyfile lists them, and `site.env` (untracked, next to `.env`) gives `deploy.sh` the address to probe.
