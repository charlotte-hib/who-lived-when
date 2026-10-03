# Who Lived When

[![CI/CD](https://github.com/charlotte-hib/who-lived-when/actions/workflows/ci.yml/badge.svg)](https://github.com/charlotte-hib/who-lived-when/actions/workflows/ci.yml)

Live at https://wholivedwhen.charlottehibert.com

A history app you step into. Pick a moment (one place over a few years, like Paris in the 1870s or Kyoto in the 1590s), watch its story told card by card over a painting of the period, then meet everyone who lived there: rulers, artists, and the everyday lives around them. Every connection between people is a dated, sourced event. See `project-brief.html` for the original brief.

## Requirements

- Java 25 (`.java-version`, through jenv)
- Node 24 LTS (`.nvmrc`, through nvm)

## Run

Backend (Spring Boot 4, Kotlin, H2 in-memory, virtual threads) on http://localhost:8080:

```sh
cd backend
./gradlew bootRun
```

Frontend (Next.js 16) on http://localhost:3000:

```sh
cd frontend
nvm use
cp .env.example .env.local
npm install
npm run dev
```

Or both in Docker, as they run in production, on http://localhost:3000:

```sh
docker compose up --build
```

## How it works

- **Moments are the way in.** A `Moment` is a place over a few years with a hook, a public-domain painting, "the world around" it (who governs, everyday life, arts and ideas, meanwhile elsewhere), a story of ordered `StoryCard`s (scene, person, everyday life, documented event) and curated `Door`s to other moments ("Meanwhile, elsewhere", "Follow Zola and Clemenceau"). Moments without a written story get doors to the nearest moments, here and elsewhere.
- **People live in their world.** A person's page shows the world around them and "their life in their time": who governed when they were born, each change of regime and each documented event, with their age. It is computed from dated records (`LifeInTime.kt`), never written by hand.
- **Facts, computed views and prose are separate.** Dates, places and events are curated data (`backend/src/main/resources/seed/*.json`, to be replaced by a Wikidata import). Bios and portraits come from the Wikipedia REST API (`WikipediaJob`: one virtual thread per lookup, at most two at a time, retrying when rate limited). Prose is drafted by Claude, then reviewed (below).

### Backend (`backend/`)

Spring Boot 4 and Kotlin, JPA entities, MapStruct for DTOs, and MockMvc API tests that run against the seed data.

### Frontend (`frontend/`)

Next.js 16 App Router with server components for data. The story player, the moment's year slider (Base UI) and search are client components. shadcn/ui, `motion` for transitions, Lucide icons, and a dark "gallery" theme.

## Drafting a story with Claude

Stories are prose, so they are drafted by Claude and reviewed by a person before they ship:

```sh
cd backend
export ANTHROPIC_API_KEY=...
./gradlew bootRun --args='--app.drafting.moment=edo-1830s --server.port=0'
```

`StoryDraftJob` gathers the moment's sources (Wikipedia leads of the people alive there, its documented events, eras and typical lives, and people alive elsewhere), asks `claude-opus-5-5` for a story as structured output where every line carries a quote from a source, then checks every quote and reference (`StoryDraftValidator`). Nothing is published: the draft and the validator's findings go to `backend/drafts/<moment>.json` for a curator to correct and copy into `seed/moments.json`. The request opts into server-side refusal fallbacks (`fallbacks: "default"`).

## API

- `GET /api/moments` lists published moments, with who governed, the story length and its cast.
- `GET /api/moments/{id}` returns a moment: the world around it, its eras, people, everyday lives, events and doors.
- `GET /api/moments/{id}/story` returns its story cards and doors (404 when no story is written yet).
- `GET /api/people/{slug}?year=1875` returns a person in the world around them in that year (their prime if no year is given), with their life in their time.
- `GET /api/years/{year}/people?exclude=FR` returns a few people alive that year in each other region.
- `GET /api/search?q=zola` searches people and moments, ignoring accents.
- `GET /api/regions`, `GET /api/eras`, `GET /api/eras/{id}`.
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:wholivedwhen`).

## Deployment

One small VPS (1 vCPU, 2 GB) runs the app with Docker Compose next to other sites. The server's Caddy serves them all and gets their HTTPS certificates; it imports this app's site from `deploy/Caddyfile` and proxies it to the Next.js frontend on localhost, which forwards `/api/*` to the backend. The backend has no published port, and the production profile turns the H2 console off.

The pipeline (`.github/workflows/ci.yml`) holds no long-lived secrets:

1. **Every pull request** runs the backend tests and the frontend lint and build.
2. **On `main`**, both images are built once, pushed to GHCR tagged with the commit, and given a signed SLSA build provenance attestation and a signed SPDX SBOM (GitHub artifact attestations, Sigstore).
3. **The deploy job** (GitHub environment `production`) verifies each image's provenance with `gh attestation verify`, pins it by digest, joins the tailnet through Tailscale workload identity federation (GitHub OIDC, no auth key), and runs `deploy/deploy.sh` over Tailscale SSH (no SSH key). SSH is closed on the public interface.
4. **`deploy/deploy.sh`** checks out the commit's Compose config, starts the pinned images, and probes the site through the server's Caddy. If it is not healthy within three minutes, it rolls back to the previous release and fails the job.

Dependabot keeps Actions (pinned by commit), Gradle, npm and base images up to date.

Inspect what runs in production:

```sh
gh attestation verify oci://ghcr.io/charlotte-hib/who-lived-when-backend:latest --repo charlotte-hib/who-lived-when
```
