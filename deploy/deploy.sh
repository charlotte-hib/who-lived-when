#!/usr/bin/env bash
# Rolls out one release on the VPS, then rolls back to the previous one if the site is not healthy.
# Run by the deploy workflow over Tailscale SSH, as the deploy user, from the repository checkout at that commit:
#   deploy/deploy.sh <commit sha> <backend image@digest> <frontend image@digest> < <release bundle, .tar.gz>
# The release (commit, image digests and data directory) is written to .env, which Docker Compose reads.
# The data comes from the private release bundle the commit's release.lock pins, which the workflow pulls and sends on
# stdin; it is unpacked into releases/<RELEASE_ID>, and the three newest are kept. A commit without release.lock serves
# its own sample/.
# The site address to probe comes from site.env (SITE_ADDRESS=...), kept on the server only.
# The database's passwords are in db.env (the superuser's, POSTGRES_PASSWORD=...) and db-roles.env (the backend's
# two roles, SITE_OWNER_PASSWORD and SITE_READER_PASSWORD), which this script fills in when missing and the server keeps.
# The release's schema is named after RELEASE_SHA.
# Prometheus and Grafana run once the server has grafana.env (GF_SECURITY_ADMIN_PASSWORD=...), kept on the
# server only too; they start after the release is healthy and never cause a rollback.
set -euo pipefail

# Everything runs inside functions: bash reads them whole, before `git checkout` rewrites this file.
main() {
  cd "$(dirname "$0")/.."
  [[ -f site.env ]] && SITE=$(value SITE_ADDRESS site.env)
  [[ -n ${SITE:-} ]] || { echo "No SITE_ADDRESS in site.env" >&2; exit 1; }
  passwords
  [[ -f .env ]] && cp .env .env.previous

  local data
  if data=$(receive) && release "$1" "$2" "$3" "$data" && healthy; then
    monitor
    prune
    docker image prune --force > /dev/null
    echo "Released $1"
    return
  fi

  if [[ -f .env.previous ]]; then
    local previous
    previous=$(value RELEASE_SHA .env.previous)
    echo "Release $1 is unhealthy, rolling back to $previous" >&2
    release "$previous" "$(value BACKEND_IMAGE .env.previous)" "$(value FRONTEND_IMAGE .env.previous)" \
      "$(value RELEASE_DIR .env.previous)"
  fi
  exit 1
}

# Unpacks the bundle on stdin into the directory of release.lock's RELEASE_ID, unless the server has it already, and
# prints that directory. Through a temporary directory, so a transfer cut short never looks like a release.
receive() {
  [[ -f release.lock ]] || return 0
  local id
  id=$(value RELEASE_ID release.lock)
  [[ -n $id ]] || { echo "No RELEASE_ID in release.lock" >&2; return 1; }
  if [[ ! -d releases/$id ]]; then
    rm -rf "releases/.$id" && mkdir -p "releases/.$id" && tar -xz -C "releases/.$id" &&
      mv "releases/.$id" "releases/$id" || return 1
  fi
  # Deployed last, so kept longest (prune).
  touch "releases/$id" && echo "./releases/$id"
}

# Checks out the release's compose config and starts its images on its data (none: sample/). Chained with &&
# because set -e does not apply inside an if condition. The data directory must exist: Docker would mount an empty
# one in its place, and an empty release looks healthy. Only the site's own services: the monitoring ones, when on,
# stay in the Compose project (so they are not orphans) but start in monitor().
release() {
  [[ -z $4 || -d $4 ]] &&
    git checkout --quiet --detach "$1" &&
    { printf 'RELEASE_SHA=%s\nBACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\nRELEASE_DIR=%s\n' "$1" "$2" "$3" "$4" &&
      profiles; } > .env &&
    docker compose pull --quiet backend frontend &&
    docker compose up --detach --remove-orphans --wait backend frontend
}

# Random passwords for the database, readable by the deploy user only, for the ones missing. Postgres reads the
# superuser's when it creates its data directory, in the db-data volume: a new one there needs that volume removed
# too. The roles' passwords are set again on every start (db/roles.sql).
passwords() {
  password db.env POSTGRES_PASSWORD &&
    password db-roles.env SITE_OWNER_PASSWORD &&
    password db-roles.env SITE_READER_PASSWORD
}

password() {
  [[ -f $1 && -n $(value "$2" "$1") ]] && return
  (umask 077 && printf '%s=%s\n' "$2" "$(od -An -tx1 -N32 /dev/urandom | tr -d ' \n')" >> "$1")
}

# Keeps the three most recently deployed releases on disk, like the three newest schemas in the database, and always
# the current and previous ones, so a rollback finds its data.
prune() {
  local current previous='' dir
  current=$(value RELEASE_DIR .env)
  [[ -f .env.previous ]] && previous=$(value RELEASE_DIR .env.previous)
  for dir in $(ls -dt releases/*/ 2> /dev/null | tail -n +4); do
    dir=./${dir%/}
    [[ $dir == "$current" || $dir == "$previous" ]] || rm -rf "$dir"
  done
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
