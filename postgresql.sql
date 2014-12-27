create schema intel;

create type resonator as(
  energy integer,
  level smallint,
  owner varchar
);

create type mod as(
  name varchar,
  owner varchar,
  rarity varchar,
  stats varchar
);

create table intel.geohash(
  geohash char(5) not null primary key,
  late5 integer not null,
  lnge5 integer not null,
  country char(2) not null,
  state varchar not null,
  city varchar not null
) with(oids=false);

create table intel.logs(
  id serial not null primary key,
  method varchar not null,
  content jsonb not null,
  request varchar not null,
  response varchar not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create table intel.portals(
  id bigint not null primary key,
  guid varchar not null unique,
  region_id varchar not null,
  nearly_geohash char(5) null,
  latE6 integer not null,
  lngE6 integer not null,
  title varchar not null,
  image varchar not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  deleted_at timestamp null
) with(oids=false);

create unique index portal_idx00 on intel.portals(guid)

create table intel.agents(
  id serial not null primary key,
  name varchar not null unique,
  team char(1) not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create unique index agents_idx00 on intel.agents(name);

create table intel.portal_state_logs(
  id serial not null primary key,
  portal_id integer not null references intel.portals(id) on delete cascade,
  owner varchar not null,
  level smallint not null,
  health smallint not null,
  team char(1) not null default 'N',
  mitigation smallint not null,
  res_count smallint not null,
  resonators jsonb not null,
  mods jsonb not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);
