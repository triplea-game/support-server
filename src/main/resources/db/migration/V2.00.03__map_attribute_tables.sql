-- Faceted attribute model for maps:
--   map_attribute       — the dimension (e.g. "difficulty", "era")
--   map_attribute_value — the allowed values within a dimension (e.g. "easy")
--   map_index_attribute — assigns one value per attribute to a map (single-valued)

create table map_attribute
(
    id            serial      primary key,
    name          varchar(64) not null unique,
    display_order int         not null check (display_order between 0 and 1000)
);

create table map_attribute_value
(
    id               serial      primary key,
    map_attribute_id int         not null references map_attribute(id) on delete cascade,
    value            varchar(64) not null,
    display_order    int         not null check (display_order between 0 and 1000),
    unique (map_attribute_id, value),
    -- Supports the composite FK on map_index_attribute below, which guarantees
    -- the assigned value actually belongs to the claimed attribute.
    unique (id, map_attribute_id)
);

create table map_index_attribute
(
    map_index_id           int not null references map_index(id) on delete cascade,
    map_attribute_id       int not null,
    map_attribute_value_id int not null,
    primary key (map_index_id, map_attribute_id),
    foreign key (map_attribute_value_id, map_attribute_id)
        references map_attribute_value(id, map_attribute_id) on delete cascade
);
comment on table map_index_attribute is 'Assigns one attribute value per attribute to a map';
