-- Drops the original tag schema in preparation for the rename to attributes.
-- Order matters: child rows first.
drop table if exists map_index_tag;
drop table if exists map_tag;
drop table if exists map_tag_category;
