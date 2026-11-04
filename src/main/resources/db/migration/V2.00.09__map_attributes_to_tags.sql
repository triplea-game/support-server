-- Tags replace the map attributes (issue #139): every attribute value becomes a tag of the map_tag
-- list, including values no map uses, and every map's attribute values become its tags.
--
-- Tag names: the value text, or '<attribute name>: <value>' when the same value text exists in
-- more than one attribute. A tag that already has exactly that name is reused. New tags go after
-- the existing ones, 10 apart, in attribute order and then value order.
--
-- The attribute tables (map_attribute, map_attribute_value, map_index_attribute) stay: the previous
-- release still reads and writes them after a rollback. A later release drops them.

create temporary table attribute_value_tag as
select v.id as map_attribute_value_id,
       case when count(*) over (partition by v.value) > 1 then a.name || ': ' || v.value
            else v.value
       end as tag_name,
       row_number() over (order by a.display_order, a.id, v.display_order, v.id) as position
from map_attribute_value v
join map_attribute a on a.id = v.map_attribute_id;

insert into map_tag (name, display_order)
select tag_name,
       coalesce((select max(display_order) from map_tag), -10)
           + 10 * row_number() over (order by position)
from attribute_value_tag
where tag_name not in (select name from map_tag)
order by position
on conflict (name) do nothing;

insert into map_index_tag (map_index_id, map_tag_id)
select mia.map_index_id, t.id
from map_index_attribute mia
join attribute_value_tag avt on avt.map_attribute_value_id = mia.map_attribute_value_id
join map_tag t on t.name = avt.tag_name
on conflict do nothing;

drop table attribute_value_tag;
