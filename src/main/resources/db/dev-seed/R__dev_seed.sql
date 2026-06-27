-- Repeatable Flyway migration: re-runs whenever this file's checksum changes.
-- Active only under the dev profile via:
--   %dev.quarkus.flyway.locations=db/migration,db/dev-seed
-- Truncate + reinsert so `make run` always boots into a known seed state, even
-- when the testcontainers Postgres is reused across restarts.

truncate table map_index_attribute, map_index, map_attribute_value, map_attribute restart identity cascade;
truncate table error_report_history restart identity;

insert into map_attribute (name, display_order) values
  ('era',        10),
  ('difficulty', 20);

insert into map_attribute_value (value, map_attribute_id, display_order) values
  ('ancient',  (select id from map_attribute where name = 'era'),        1),
  ('medieval', (select id from map_attribute where name = 'era'),        2),
  ('modern',   (select id from map_attribute where name = 'era'),        3),
  ('easy',     (select id from map_attribute where name = 'difficulty'), 1),
  ('medium',   (select id from map_attribute where name = 'difficulty'), 2),
  ('hard',     (select id from map_attribute where name = 'difficulty'), 3);

insert into map_index (
  map_name, last_commit_date, repo_url, preview_image_url,
  description, download_size_bytes, download_url, default_branch
) values
  ('270 BC',
   now() - interval '7 days',
   'https://github.com/triplea-maps/270_bc',
   'https://raw.githubusercontent.com/triplea-maps/270_bc/master/preview.png',
   'Conflict in the ancient Mediterranean during the rise of Rome.',
   1048576,
   'https://github.com/triplea-maps/270_bc/archive/master.zip',
   'master'),
  ('Medieval',
   now() - interval '30 days',
   'https://github.com/triplea-maps/medieval',
   'https://raw.githubusercontent.com/triplea-maps/medieval/master/preview.png',
   'Feudal kingdoms vie for control of medieval Europe.',
   2097152,
   'https://github.com/triplea-maps/medieval/archive/master.zip',
   'master'),
  ('World War II Global',
   now() - interval '2 days',
   'https://github.com/triplea-maps/world_war_ii_global',
   'https://raw.githubusercontent.com/triplea-maps/world_war_ii_global/master/preview.png',
   'The classic global WWII scenario, Axis vs Allies.',
   4194304,
   'https://github.com/triplea-maps/world_war_ii_global/archive/master.zip',
   'master');

insert into map_index_attribute (map_index_id, map_attribute_id, map_attribute_value_id)
select mi.id, av.map_attribute_id, av.id
from map_index mi
join (values
        ('270 BC',              'ancient'),
        ('270 BC',              'medium'),
        ('Medieval',            'medieval'),
        ('Medieval',            'easy'),
        ('World War II Global', 'modern'),
        ('World War II Global', 'hard')
     ) as t(map_name, attribute_value) on mi.map_name = t.map_name
join map_attribute_value av on av.value = t.attribute_value;

insert into error_report_history
  (user_ip, system_id, report_title, game_version, created_issue_link, date_created) values
  ('10.0.0.1', 'sys-dev-001', 'NPE on game start',               '2.7.18234',
   'https://github.com/triplea-game/triplea/issues/9001', now() - interval '2 days'),
  ('10.0.0.2', 'sys-dev-002', 'Map download fails for Medieval', '2.7.18234',
   'https://github.com/triplea-game/triplea/issues/9002', now() - interval '1 day'),
  ('10.0.0.3', 'sys-dev-003', 'AI crashes on turn 5',            '2.7.18301',
   'https://github.com/triplea-game/triplea/issues/9003', now() - interval '6 hours');
