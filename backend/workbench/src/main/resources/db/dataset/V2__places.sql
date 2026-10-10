-- The places people were born, died, worked or lived in (person_place), and where they lie today: by their
-- coordinates in Natural Earth's de facto borders, and by Wikidata's current country. A place Wikidata no longer has is
-- missing, as occupations can be.
create table place (
    qid text primary key,
    label text not null,
    label_fr text,
    -- P625, best rank first. Ancient places often have none.
    latitude double precision,
    longitude double precision,
    -- The ISO 3166-1 code of the country the coordinates lie in, or lie just off (app.natural-earth.coast-km).
    coordinates_country text,
    -- Natural Earth's disputed area the coordinates lie in, with who administers and claims it.
    disputed_area text
);

-- Its current countries: P17 with no end time, best rank first, with the ISO code Natural Earth gives the country.
-- None for a state of the past, such as the Russian Empire.
create table place_country (
    place text not null references place,
    position smallint not null,
    country text not null,
    iso text,
    primary key (place, position)
);

-- Why a place needs a person's eye: a flag of the review policy, and the reason for it.
create table place_flag (
    place text not null references place,
    reason text not null,
    flag text not null,
    primary key (place, reason)
);
