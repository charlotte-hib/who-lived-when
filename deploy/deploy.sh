#!/usr/bin/env bash
# Rolls out one release on the VPS, then rolls back to the previous one if the site is not healthy.
# Run by the deploy workflow over Tailscale SSH, as the deploy user, from the repository checkout:
#   deploy/deploy.sh <commit sha> <backend image@digest> <frontend image@digest>
# The release (commit and image digests) is written to .env, which Docker Compose reads.
set -euo pipefail

# The sslip.io address until the charlottehibert.com subdomain has DNS records (see deploy/Caddyfile).
SITE=${SITE_ADDRESS:-51-254-125-102.sslip.io}

# Everything runs inside functions: bash reads them whole, before `git checkout` rewrites this file.
main() {
  cd "$(dirname "$0")/.."
  [[ -f .env ]] && cp .env .env.previous

  if release "$@" && healthy; then
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
# set -e does not apply inside an if condition.
release() {
  git checkout --quiet --detach "$1" &&
    printf 'RELEASE_SHA=%s\nBACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$1" "$2" "$3" > .env &&
    docker compose pull --quiet &&
    docker compose up --detach --remove-orphans --wait
}

# The whole path a visitor takes: Caddy (TLS), Next.js, then the Spring Boot API. Waits up to 3 minutes.
healthy() {
  local attempt
  for attempt in $(seq 36); do
    if probe / && probe /api/moments; then return 0; fi
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
