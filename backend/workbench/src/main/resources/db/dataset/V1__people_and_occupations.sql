-- The fetched dataset: what the workbench mapped from raw, facts only, with no decision applied. Rebuilt from raw on
-- every mapping run (DatasetMapping), so nothing here needs keeping.

-- People, with years as the site counts them: negative for BCE, no year 0.
create table person (
    qid text primary key,
    label text not null,
    label_fr text,
    sitelinks integer not null,
    born integer not null,
    -- Wikidata's precision: 11 a day, 10 a month, 9 a year, 8 a decade, 7 a century.
    born_precision smallint not null,
    died integer not null,
    -- Null when the year of death is estimated.
    died_precision smallint,
    died_estimated boolean not null,
    -- A date coarser than a year, a "circa", or an estimated death.
    dates_approximate boolean not null,
    -- Sex or gender (P21), only to choose the French feminine form of an occupation.
    gender text,
    enwiki text,
    frwiki text
);

-- Occupations (P106) in Wikidata's order, best rank first. Not every one is in occupation: Wikidata may have deleted it.
create table person_occupation (
    person text not null references person,
    position smallint not null,
    occupation text not null,
    referenced boolean not null,
    primary key (person, position)
);

-- Places of birth (P19), death (P20), work (P937) and residence (P551), with years when stated.
create table person_place (
    person text not null references person,
    property text not null,
    position smallint not null,
    place text not null,
    start_year integer,
    end_year integer,
    referenced boolean not null,
    primary key (person, property, position)
);

-- Why a person's facts need a person's eye: a flag of the review policy, and the reason for it.
create table person_flag (
    person text not null references person,
    reason text not null,
    flag text not null,
    primary key (person, reason)
);

-- The people's occupations, and the classes above them (P279), as far up as the import fetched them.
create table occupation (
    qid text primary key,
    label text not null,
    label_fr text,
    -- The French feminine form (P2521), for women.
    female_label_fr text
);

create table occupation_parent (
    occupation text not null references occupation,
    position smallint not null,
    parent text not null references occupation,
    primary key (occupation, position)
);
