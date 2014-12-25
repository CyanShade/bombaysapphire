create table intel.logs(
  id serial not null,
  method varchar not null,
  log jsonb not null,
  created_at timestamp with time zone
) with(oids=false);


create table portals(
  id serial not null primary key,
  guid varchar not null,
  title varchar not null,
  latE6 integer not null,
  lngE6 integer not null,
  image varchar not null,
  ornaments varchar not null,
  created_at timestamp not null default current_timestamp
) without oids;

create table portal_activities(
  id serial not null primary key,
  portal_id integer not null,
  owner varchar not null,
  res_count smallint not null,
  level smallint not null,
  health smallint not null,
  team char(1) not null default 'N',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  deleted_at timestamp
) without oids;

create table portals(
  id serial not null primary key,
  guid varchar not null,
  title varchar not null,
  latE6 integer not null,
  lngE6 integer not null,
  image varchar not null,
  ornaments varchar not null,
  owner varchar not null,
  res_count smallint not null,
  level smallint not null,
  health smallint not null,
  team char(1) not null default 'N',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  deleted_at timestamp
) without oids;

create table mods(
  id serial not null primary key,
  portal_id integer not null,
  slot smallint  not null,
  name varchar not null,
  owner varchar not null,
  rarity varchar not null,
  stats varchar not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
) without oids;

create table resonators(
  id serial not null primary key,
  portal_id integer not null,
  energy smallint not null,
  level smallint not null,
  owner varchar not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
) without oids;
