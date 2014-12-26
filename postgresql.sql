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

create table intel.logs(
  id serial not null primary key,
  method varchar not null,
  content jsonb not null,
  request varchar not null,
  response varchar not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create table intel.portals(
  id serial not null primary key,
  guid varchar not null unique,
  region_id varchar not null,
  latE6 integer not null,
  lngE6 integer not null,
  title varchar not null,
  image varchar not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  deleted_at timestamp null
) with(oids=false);

create table intel.agents(
  id serial not null primary key,
  name varchar not null unique,
  team char(1) not null,
  created_at timestamp not null default current_timestamp
) with(oids=false);

create unique index intel.agents_idx on intel.agents(name);

create table intel.portal_activities(
  id serial not null primary key,
  portal_id integer not null,
  owner varchar not null,
  level smallint not null,
  health smallint not null,
  team char(1) not null default 'N',
  mitigation smallint not null,
  res_count smallint not null,
  resonator0 resonator null,
  resonator1 resonator null,
  resonator2 resonator null,
  resonator3 resonator null,
  resonator4 resonator null,
  resonator5 resonator null,
  resonator6 resonator null,
  resonator7 resonator null,
  mod0 mod null,
  mod1 mod null,
  mod2 mod null,
  mod3 mod null,
  created_at timestamp not null default current_timestamp
) with(oids=false);
