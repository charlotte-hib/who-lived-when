-- Search on people's names, accent-insensitive ("zola" finds Émile Zola) and typo-tolerant ("cezane" finds Paul
-- Cézanne), with a trigram index.

-- Extensions belong to the database, not to a schema that a newer release drops: in a schema of their own, shared by
-- every release schema. Both are trusted, so the schemas' owner may create them.
create schema if not exists extensions;
create extension if not exists unaccent with schema extensions;
create extension if not exists pg_trgm with schema extensions;
grant usage on schema extensions to ${reader};

-- "Émile Zola" becomes "emile zola". unaccent itself is only stable (its dictionary could change), so it cannot be used
-- in a generated column or an index: this wrapper names the dictionary and is immutable.
create function fold_for_search(text) returns text
    language sql immutable parallel safe strict
    return lower(extensions.unaccent('extensions.unaccent'::regdictionary, $1));

-- Stored: Postgres 18's generated columns are virtual by default, and those cannot be indexed.
alter table person add column search_name text generated always as (fold_for_search(name)) stored;

create index person_search_name_idx on person using gin (search_name extensions.gin_trgm_ops);
