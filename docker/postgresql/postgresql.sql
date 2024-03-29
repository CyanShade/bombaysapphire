create schema intel;
-- このファイルを書き換えるなどして DB のテーブルを直接変更した場合は org.koiroha.bombaysapphire.tools.Schema を再実行すること。

create table intel.geohash(
  geohash char(10) not null primary key,
  late6 integer not null,
  lnge6 integer not null,
  country char(2) not null,
  state varchar not null,
  city varchar not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create table intel.heuristic_regions(
  id serial not null primary key,
  country char(2) not null,
  state varchar null,
  city varchar null,
  side char(1) not null, -- 内側の場合 I, 外側の場合 O
  seq integer not null, -- 同一の行政区内で複数の多角形に分かれる場合の枝番
  region polygon not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  constraint heuristic_regions_country_state_city_seq_side_key unique (country, state, city, seq, side)
) with(oids=false);
COMMENT ON COLUMN intel.heuristic_regions.side IS '内側の場合 I, 外側の場合 O';
COMMENT ON COLUMN intel.heuristic_regions.seq  IS '同一の行政区内で複数の多角形に分かれる場合の枝番';

create table intel.logs(
  id bigserial not null primary key,
  method varchar not null,
  content jsonb not null,
  request varchar not null,
  response varchar not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create table intel.portals(
  id serial not null primary key,
  guid varchar not null unique,
  tile_key varchar not null,
  geohash char(10) null references intel.geohash(geohash) on delete set null,
  late6 integer not null,
  lnge6 integer not null,
  title varchar not null,
  image varchar not null,
  -- 現在の状態
  team char(1) not null,
  level smallint not null,
  mods jsonb,
  guardian bigint not null,
  created_at timestamp not null default current_timestamp,
  verified_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  deleted_at timestamp null
) with(oids=false);

create unique index portal_idx00 on intel.portals(guid);
create unique index portal_idx01 on intel.portals(tile_key);
create unique index portal_idx02 on intel.portals(created_at);

create table intel.portal_event_logs(
  id serial not null primary key,
  portal_id integer not null references intel.portals(id) on delete cascade,
  action varchar not null,
  old_value varchar,
  new_value varchar,
  verified_at timestamp,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create index portal_event_idx00 on intel.portal_event_logs(portal_id);

create table intel.portal_state_logs(
  id serial not null primary key,
  portal_id integer not null references intel.portals(id) on delete cascade,
  owner varchar,
  level smallint not null,
  health smallint not null,
  team char(1) not null default 'N',
  mitigation smallint,
  res_count smallint not null,
  resonators jsonb,
  mods jsonb,
  artifact jsonb,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create index portal_state_idx00 on intel.portal_state_logs(portal_id);

create table intel.plexts(
  id bigserial not null primary key,
  guid varchar not null unique,
  unknown float not null,
  category integer not null,
  markup jsonb not null,
  plext_type varchar not null,
  team char(1) not null,
  text varchar not null,
  created_at timestamp not null default current_timestamp
);

create unique index plexts_guid on intel.plexts(guid);

create table intel.farms(
  id serial not null primary key,
  parent integer,
  name varchar not null,
  address varchar not null default '',
  description text not null,
  formatted_description text not null,
  icon bytea,
  external_kml_url text,
  latest_activity integer,  -- references intel.farm_activities(id) on delete set null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
) with(oids=false);

create index farms_idx01 on intel.farms(parent);
create index farms_idx02 on intel.farms(name);

create table intel.farm_regions(
  id serial not null primary key,
  farm_id integer not null references intel.farms(id) on delete cascade,
  region polygon not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create index farm_regions_idx01 on intel.farm_regions(farm_id);

create table intel.farm_portals(
  id serial not null primary key,
  farm_id integer not null references intel.farms(id) on delete cascade,
  portal_id integer not null references intel.portals(id) on delete cascade
) with(oids=false);

create index farm_portals_idx01 on intel.farm_portals(farm_id);
create index farm_portals_idx02 on intel.farm_portals(portal_id);

create table intel.farm_activities(
  id serial not null primary key,
  farm_id integer not null references intel.farms(id) on delete cascade,
  strict_portal_count integer not null default 0,
  portal_count integer not null default 0,
  portal_count_r integer not null default 0,
  portal_count_e integer not null default 0,
  p8_reach_r integer not null default 0,
  p8_reach_e integer not null default 0,
  avr_res_level_r real not null default 0,
  avr_res_level_e real not null default 0,
  avr_resonator_r real not null default 0,
  avr_resonator_e real not null default 0,
  avr_mod_r real not null default 0,
  avr_mod_e real not null default 0,
  avr_mitigation_r real not null default 0,
  avr_mitigation_e real not null default 0,
  avr_cooldown_ratio real not null default 0,
  additional_hack integer not null default 0,
  measured_at timestamp not null default current_timestamp,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create index farm_activities_idx00 on intel.farm_activities(farm_id);
create index farm_activities_idx01 on intel.farm_activities(created_at);

create table intel.sentinel_tasks(
  id serial not null primary key,
  priority smallint not null,
  tag varchar(64) not null,
  script text not null,
  wait_after bigint not null,
  expired_at timestamp,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create index sentinel_tasks_idx00 on intel.sentinel_tasks(priority);
create index sentinel_tasks_idx01 on intel.sentinel_tasks(tag);

-- 以下未実装

create table intel.agents(
  id serial not null primary key,
  name varchar not null unique,
  team char(1) not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create unique index agents_idx00 on intel.agents(name);
