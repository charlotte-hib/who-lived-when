-- What the workbench fetches from Wikimedia, kept as it came. Unlike a release schema, built again from sample/ on
-- every start, this schema is never dropped: fetching it all again takes hours.

-- People the query service finds above the sitelinks cut-off.
create table discovered (
    qid text primary key,
    sitelinks integer not null,
    discovered_at timestamptz not null,
    -- The last import that found them. One not found since has dropped below the cut-off, or out of Wikidata.
    seen_at timestamptz not null
);

-- Wikidata entities as wbgetentities returned them, at the latest revision fetched.
create table entity (
    qid text primary key,
    revision bigint not null,
    fetched_at timestamptz not null,
    json jsonb not null
);

-- One import: discovery, then the people's entities, then the entities their claims link to. A run that stopped
-- resumes where it was.
create table import_run (
    id bigint generated always as identity primary key,
    started_at timestamptz not null default now(),
    phase text not null,
    -- Every request sent to Wikimedia, retries included.
    requests integer not null default 0,
    -- HTTP 429 and maxlag answers: Wikimedia asked to wait.
    busy integer not null default 0,
    -- Entities stored: new, or at a new revision.
    fetched integer not null default 0,
    -- Entities whose revision had not changed, so not fetched again.
    unchanged integer not null default 0,
    -- Entities Wikidata no longer has.
    missing integer not null default 0,
    -- Until when Wikimedia last asked to wait. A run resumed after a crash waits until then too.
    not_before timestamptz,
    finished_at timestamptz
);

-- The slices of birth dates a run has had from the query service. Dates in ISO 8601 with astronomical years, as SPARQL
-- writes them (text, because Postgres dates have no year 0).
create table discovery_slice (
    run_id bigint not null references import_run,
    born_from text not null,
    born_until text not null,
    people integer not null,
    primary key (run_id, born_from)
);

-- The entities a run fetches, or checks for a new revision: PEOPLE, then LINKED. done_at is set once stored.
create table import_item (
    run_id bigint not null references import_run,
    qid text not null,
    kind text not null,
    done_at timestamptz,
    primary key (run_id, qid)
);

create index import_item_to_do on import_item (run_id, kind, qid) where done_at is null;
