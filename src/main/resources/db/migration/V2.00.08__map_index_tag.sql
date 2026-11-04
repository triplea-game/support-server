-- Assigns tags of the map_tag list to maps; a map can have any number of tags.
create table map_index_tag
(
    map_index_id int not null references map_index(id) on delete cascade,
    map_tag_id   int not null references map_tag(id) on delete cascade,
    primary key (map_index_id, map_tag_id)
);
comment on table map_index_tag is 'Assigns tags of the map_tag list to maps';
