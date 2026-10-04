# Who Lived When

[![CI/CD](https://github.com/charlotte-hib/who-lived-when/actions/workflows/ci.yml/badge.svg)](https://github.com/charlotte-hib/who-lived-when/actions/workflows/ci.yml)

A history app you step into. Pick a moment (one place over a few years, like Paris in the 1870s or Kyoto in the 1590s), watch its story told card by card over a painting of the period, then meet everyone who lived there: rulers, artists, and the everyday lives around them. Every connection between people is a dated, sourced event.

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

Or both in Docker, on http://localhost:3000:

```sh
docker compose up --build
```

## End-to-end tests

A few Playwright tests (`e2e/`) walk the main journeys in a desktop browser and on a phone with a touchscreen: playing a story by tapping its cards, searching for someone, and opening who was at a documented event. They run against a site that is already up (`BASE_URL`, http://localhost:3000 by default), so start the app first, in Docker or with the two commands above:

```sh
docker compose -f docker-compose.yml -f e2e/compose.yaml up --build --detach --wait   # optional: skips Wikipedia
cd e2e
nvm use
npm ci
npx playwright test
npx playwright show-report   # after a failure: traces and screenshots
```

Outside CI they drive the installed Google Chrome; CI installs Playwright's Chromium. The tests live in their own package, outside `frontend/`, so none of it ends up in an image.

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
- `POST /api/events` counts one anonymous visitor event (see [Metrics](#metrics)): 204 when counted, 400 when outside the allowed names and values, 413 above 1 KB.
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:wholivedwhen`).

### API tests

`http/api.http` is a short end-to-end smoke suite: a few requests against a running backend, each with assertions on the status, the content type and the seed data it returns. Start the backend without Wikipedia enrichment (`./gradlew bootRun --args='--app.wikipedia.enrich=false'`), then either open the file in IntelliJ and run the requests with the `local` environment, or run them all with the [HTTP Client CLI](https://www.jetbrains.com/help/idea/http-client-cli.html):

```sh
ijhttp --env-file http/http-client.env.json --env local http/api.http
```

CI runs the same file against the backend image (the "API tests" job), never against production.

## Metrics

Two Grafana dashboards, provisioned from `monitoring/`: **Visitors** (page views, stories started and finished, the card where readers stop, people opened in a panel or on their full page and from where, searches) and **Service** (requests, p95 latency, 5xx, JVM memory, CPU, GC).

- **What is counted.** The frontend (`lib/analytics.ts`) sends a few events with `navigator.sendBeacon` to `POST /api/events`: `page_view` (by page template: home, moment, story, person), `story_started`, `story_card_reached` (card number), `story_completed`, `person_panel_opened` and `person_full_page_opened` (where from: the page underneath, search, the panel, a link from outside), `search_used` and `search_result_opened`. The backend (`VisitorEvents.kt`) only adds one to a Prometheus counter; every label comes from a closed set (page templates, published moments, card positions within their story), and anything else is rejected and counted as rejected.
- **What is not.** No cookies, no ids, no IP addresses, user agents, URLs or referrers are sent or stored, and nothing links two events to the same visitor: the counters only say how often something happened. The only thing kept in the browser is a one-shot flag in `sessionStorage` that tells a person's full page it was opened from the panel; the page removes it on arrival. Automated browsers (`navigator.webdriver`) send nothing.
- **Where it runs.** Spring Boot Actuator serves `/actuator/prometheus` (and health) on its own port, 8081, in the Docker image; nothing publishes that port, and the frontend only forwards `/api/*` to 8080. Prometheus (30 days, at most 1 GB) and Grafana run in Compose under the `monitoring` profile, with small memory limits. Grafana listens on `127.0.0.1:3001` only, never through Caddy, with sign-up and anonymous access off.

Locally, with a password of your choice in `grafana.env` (untracked):

```sh
echo "GF_SECURITY_ADMIN_PASSWORD=$(openssl rand -base64 24)" > grafana.env
docker compose --profile monitoring up --build
```

Then open http://localhost:3001 and sign in as `admin`. On the server, `deploy/deploy.sh` starts Prometheus and Grafana once `grafana.env` exists next to `.env`; reach Grafana through an SSH tunnel (`ssh -L 3001:localhost:3001 <server>`, then http://localhost:3001). To serve it under another address, for example through `tailscale serve`, add `GF_SERVER_ROOT_URL=<that address>` to the server's `grafana.env`, which overrides `monitoring/grafana/defaults.env`. The dashboards are read from the repository: edit them in Grafana, export the JSON into `monitoring/grafana/dashboards/` and commit it.

## Deployment

The pipeline (`.github/workflows/ci.yml`) holds no long-lived secrets:

1. **Every pull request** runs the backend tests and the frontend lint and build, then builds each image once (saved for a day as a workflow artifact) and runs the API tests and the end-to-end tests against those exact images. `main` is protected by a ruleset: changes land only through pull requests that pass the backend tests and the frontend lint and build, with linear history and no force push or deletion.
2. **On `main`**, the same images are pushed to GHCR tagged with the commit, and given a signed SLSA build provenance attestation and a signed SPDX SBOM (GitHub artifact attestations, Sigstore).
3. **The deploy job** (GitHub environment `production`) waits for the API and end-to-end tests, verifies each image's provenance with `gh attestation verify`, pins it by digest, joins the tailnet through Tailscale workload identity federation (GitHub OIDC, no auth key), and runs `deploy/deploy.sh` over Tailscale SSH (no SSH key).
4. **`deploy/deploy.sh`** checks out the commit's Compose config, starts the pinned images, and probes the site. If it is not healthy within three minutes, it rolls back to the previous release and fails the job. Once the release is healthy, it starts or updates Prometheus and Grafana (when the server has `grafana.env`); a monitoring failure is reported but never rolls the site back.

Dependabot keeps Actions (pinned by commit), Gradle, npm and base images up to date.
