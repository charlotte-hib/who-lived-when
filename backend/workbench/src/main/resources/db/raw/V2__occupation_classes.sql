-- Occupations map to the site's domains through the classes they belong to (P279), so an import also fetches the
-- classes above each occupation, a few levels up: kind CLASSES, with how many levels above an occupation.
alter table import_item add column level smallint;
