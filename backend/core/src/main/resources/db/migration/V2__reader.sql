-- The role the site's API connects as (${reader}) may read every table of this release schema, and nothing more,
-- except filling in Wikipedia's bios and portraits (WikipediaJob). Tables added by later migrations are covered too.
grant usage on schema ${flyway:defaultSchema} to ${reader};
grant select on all tables in schema ${flyway:defaultSchema} to ${reader};
grant select on all sequences in schema ${flyway:defaultSchema} to ${reader};
alter default privileges in schema ${flyway:defaultSchema} grant select on tables to ${reader};
grant update (bio_short, about, wikipedia_url, portrait_url) on ${flyway:defaultSchema}.person to ${reader};
