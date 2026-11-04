-- The list of free map tags (e.g. "naval", "world war 2") that MapAdmins maintain, shown in
-- display_order. Assigning tags to maps comes in a later migration.
create table map_tag
(
    id            serial      primary key,
    -- names are trimmed before they are saved; a blank one would be an invisible tag
    name          varchar(64) not null unique check (name <> ''),
    display_order int         not null check (display_order >= 0)
);
