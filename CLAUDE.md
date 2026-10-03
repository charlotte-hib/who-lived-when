# Who Lived When

Spring Boot 4 + Kotlin backend (`backend/`), Next.js 16 frontend (`frontend/`, see `frontend/CLAUDE.md`). Product and design context: `README.md` and `project-brief.html`.

## Local commands

- Java 25 (jenv, `.java-version`) and Node 24 (nvm, `.nvmrc`). The shell's default Node is older: run `source ~/.nvm/nvm.sh && nvm use` in `frontend/` before any npm command.
- Backend: `cd backend && ./gradlew test` / `./gradlew bootRun` (port 8080).
- Frontend: `cd frontend && npm run lint && npm run build` / `npm run dev` (port 3000).
- Both in Docker: `docker compose up --build`, then http://localhost:3000. Docker Desktop is often not running on this Mac.

## Commits

Public repo, part of the author's CV: https://github.com/charlotte-hib/who-lived-when

- Conventional Commits, small and logical, with a body explaining why. Keep the tree clean.
- Author `charlotte-hib <9551327+charlotte-hib@users.noreply.github.com>`. No `Co-Authored-By` trailer.
- `main` is protected by the "Protect main" ruleset: no direct push, force push or deletion. Work on a branch and open a pull request; it merges (squash or rebase) once "Backend tests" and "Frontend lint and build" pass. Merging deploys to production.

## Production

https://51-254-125-102.sslip.io for now (https://wholivedwhen.charlottehibert.com once its DNS records exist), on an OVH VPS (Debian 13, 1 vCPU, 2 GB RAM + 2 GB swap) that also hosts other sites.

- **Pipeline** (`.github/workflows/ci.yml`): pull requests run backend tests and frontend lint and build. On `main`, images are built once, pushed to GHCR (`ghcr.io/charlotte-hib/who-lived-when-{backend,frontend}`, tagged `sha-<commit>`) with signed SLSA provenance and SPDX SBOM attestations. The `deploy` job (environment `production`, `main` only) verifies the provenance, joins the tailnet through Tailscale workload identity federation, and runs `deploy/deploy.sh` over Tailscale SSH. `workflow_dispatch` redeploys `main` by hand.
- **No secrets in GitHub.** Repository variables only: `TS_OAUTH_CLIENT_ID`, `TS_AUDIENCE` (Tailscale federated identity, subject `repo:charlotte-hib@9551327/who-lived-when@1402884974:environment:production`, GitHub's immutable format with owner and repo IDs), `VPS_HOST=who-lived-when-vps`. Do not add SSH keys or tokens.
- **`deploy/deploy.sh`** runs on the VPS in `/opt/who-lived-when` (a clone of this repo, owned by `deploy`): checks out the commit, writes the release (commit and image digests) to `.env`, `docker compose up`, probes `/` and `/api/moments` through Caddy, and rolls back to `.env.previous` if unhealthy within 3 minutes. Its body is in functions because it rewrites itself through `git checkout`.
- **Caddy** is installed on the server (apt, systemd), not in Compose, and serves every site on the VPS. `/etc/caddy/Caddyfile` ends with `import /opt/who-lived-when/deploy/Caddyfile`. After changing `deploy/Caddyfile`, the deploy does not reload Caddy: run `sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile && sudo systemctl reload caddy`. Never run a second proxy on ports 80/443.
- **Compose**: `backend` (no published port, `SPRING_PROFILES_ACTIVE=prod` disables the H2 console) and `frontend` on `127.0.0.1:3000`. No volumes: H2 is in memory and reseeds on every start; Wikipedia enrichment takes about a minute.

## Server access

- Public SSH is closed. The firewall is nftables only (`/etc/nftables.conf`, ufw disabled): inbound 80, 443 (tcp+udp), 41641/udp and the `tailscale0` interface; forward allows container traffic out but nothing in. The file replaces only `table inet filter`; never `flush ruleset`, which wipes Docker's and Tailscale's tables. Connect over Tailscale SSH: `ssh ovh-vps` (user `debian`, sudo) or `ssh deploy@who-lived-when-vps` (docker group). Never reopen port 22 publicly.
- Tailscale policy: `tag:ci` may reach only `tag:vps` on tcp:22, and SSH only as `deploy`.
- If Tailscale is unreachable: OVH Manager KVM console or rescue mode.
- DNS for `charlottehibert.com` is in the OVH Manager (DNS zone): `wholivedwhen` A `51.254.125.102`, AAAA `2001:41d0:401:3000::1340`.
