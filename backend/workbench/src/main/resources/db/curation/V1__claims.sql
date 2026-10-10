-- The curator's work: claims (anything a careful reader could dispute), what they rest on, and every decision taken.
-- Never dropped or rebuilt: unlike raw and dataset, nothing here can be fetched again.

create table claim (
    -- Derived from what the claim is about (person-facts:Q535, connection:Q296|Q535:friends): the same thing proposed
    -- again lands on the same claim, and a rejected one is never proposed again.
    id text primary key,
    -- The order claims were proposed in, for queues and pages.
    seq bigint generated always as identity unique,
    type text not null,
    status text not null,
    origin text not null,
    -- The record as it would ship, edits included.
    payload jsonb not null,
    -- The record as last proposed, before any edit: what a refresh compares with.
    proposal jsonb not null,
    -- Why it needs a person's eye: [{"flag": "CONFLICT", "reason": "BIRTH_DISAGREES"}].
    flags jsonb not null default '[]'
);

create index claim_queue on claim (type, status, seq);

-- What claims rest on: a Wikidata entity, a Wikipedia article, a book's page, a web page, a podcast's minute. The same
-- source supports several claims.
create table source (
    id bigint generated always as identity primary key,
    kind text not null,
    -- Its fields by kind (entity, revision, language, title, page, episode, minute, url), without the quote.
    locator jsonb not null,
    unique (kind, locator)
);

create table claim_source (
    claim text not null references claim,
    source bigint not null references source,
    position smallint not null,
    -- The passage this claim rests on. A book's quotes never leave the workbench.
    quote text,
    primary key (claim, position)
);

-- Append-only: the audit trail, and what an undo goes back to.
create table decision (
    id bigint generated always as identity primary key,
    claim text not null references claim,
    -- curator, or rule when the workbench proposed or refreshed the claim.
    decided_by text not null,
    from_status text,
    to_status text not null,
    payload_before jsonb,
    payload_after jsonb not null,
    note text,
    -- The decision an undo reverts.
    undoes bigint references decision,
    decided_at timestamptz not null default now()
);

create index decision_claim on decision (claim, id);
