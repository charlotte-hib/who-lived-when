-- The site's two database roles, run by the db-roles service (docker-compose.yml) as the superuser on every start:
-- creates them when missing, and sets their passwords from db-roles.env, so changing a password there and running
-- docker compose up applies it. psql variables: owner_password, reader_password.
\set ON_ERROR_STOP on

-- site_owner: Flyway's. Creates a schema per release, loads it, and drops the old ones.
select 'create role site_owner' where not exists (select from pg_roles where rolname = 'site_owner') \gexec
alter role site_owner with login password :'owner_password';
select format('grant create on database %I to site_owner', current_database()) \gexec

-- site_reader: the API's. Reads the release schemas, as their migrations grant it, and nothing else.
select 'create role site_reader' where not exists (select from pg_roles where rolname = 'site_reader') \gexec
alter role site_reader with login password :'reader_password';
