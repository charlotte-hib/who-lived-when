#!/usr/bin/env bash
# Rolls out one release on the VPS, then rolls back to the previous one if the site is not healthy.
# Run by the deploy workflow over Tailscale SSH, as the deploy user, from the repository checkout:
#   deploy/deploy.sh <commit sha> <backend image@digest> <frontend image@digest>
# The release (commit and image digests) is written to .env, which Docker Compose reads.
# The site address to probe comes from site.env (SITE_ADDRESS=...), kept on the server only.
# Prometheus and Grafana run once the server has grafana.env (GF_SECURITY_ADMIN_PASSWORD=...), kept on the
# server only too; they start after the release is healthy and never cause a rollback.
set -euo pipefail

# Everything runs inside functions: bash reads them whole, before `git checkout` rewrites this file.
main() {
  cd "$(dirname "$0")/.."
  [[ -f site.env ]] && SITE=$(value SITE_ADDRESS site.env)
  [[ -n ${SITE:-} ]] || { echo "No SITE_ADDRESS in site.env" >&2; exit 1; }
  [[ -f .env ]] && cp .env .env.previous

  if release "$@" && healthy; then
    monitor
    docker image prune --force > /dev/null
    echo "Released $1"
    return
  fi

  if [[ -f .env.previous ]]; then
    local previous
    previous=$(value RELEASE_SHA .env.previous)
    echo "Release $1 is unhealthy, rolling back to $previous" >&2
    release "$previous" "$(value BACKEND_IMAGE .env.previous)" "$(value FRONTEND_IMAGE .env.previous)"
  fi
  exit 1
}

# Checks out the release's compose config and starts its images. Chained with && because
# set -e does not apply inside an if condition. Only the site's own services: the monitoring
# ones, when on, stay in the Compose project (so they are not orphans) but start in monitor().
release() {
  git checkout --quiet --detach "$1" &&
    { printf 'RELEASE_SHA=%s\nBACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$1" "$2" "$3" && profiles; } > .env &&
    docker compose pull --quiet backend frontend &&
    docker compose up --detach --remove-orphans --wait backend frontend
}

# Turns on the monitoring profile when the server has a Grafana admin password.
profiles() {
  if [[ -f grafana.env && -n $(value GF_SECURITY_ADMIN_PASSWORD grafana.env) ]]; then
    echo COMPOSE_PROFILES=monitoring
  fi
}

# Starts (or updates) Prometheus and Grafana for a healthy release. The site does not need them,
# so a failure here is reported but neither fails the deploy nor rolls it back.
monitor() {
  [[ -n $(profiles) ]] || return 0
  docker compose up --detach --wait prometheus grafana || echo "Monitoring did not start; the release stands" >&2
}

# The whole path a visitor takes: Caddy (TLS), Next.js, then the Spring Boot API. Waits up to 3 minutes.
healthy() {
  local attempt
  for attempt in $(seq 36); do
    if probe / && probe '/api/search?q=a'; then return 0; fi
    sleep 5
  done
  return 1
}

probe() {
  curl --silent --fail --output /dev/null --max-time 5 --resolve "$SITE:443:127.0.0.1" "https://$SITE$1"
}

value() {
  sed -n "s/^$1=//p" "$2"
}

main "$@"
