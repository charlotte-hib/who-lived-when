-- A typical life is drawn with a public-domain image, framed for a circle, or else with an icon.
alter table life
    add column url varchar(2000),
    add column credit varchar(255),
    add column source_url varchar(2000),
    add column position varchar(255),
    add column fit varchar(255) check (fit in ('COVER', 'CONTAIN')),
    add column icon varchar(255) check (icon in (
        'AMPHORA', 'BRIEFCASE', 'CAR', 'COINS', 'CONCIERGE_BELL', 'FACTORY', 'FISH', 'HAMMER', 'LEAF', 'PICKAXE', 'SHELL',
        'SHIELD', 'SHIRT', 'SHOVEL', 'SPOOL', 'SPROUT', 'WHEAT'
    ));
