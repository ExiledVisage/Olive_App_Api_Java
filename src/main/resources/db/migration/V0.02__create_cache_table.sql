-- Laravel: 0001_01_01_000001_create_cache_table.php

create table "cache" (
    "key" varchar(255) not null,
    "value" text not null,
    "expiration" integer not null
);

alter table "cache" add primary key ("key");
create index "cache_expiration_index" on "cache" ("expiration");

create table "cache_locks" (
    "key" varchar(255) not null,
    "owner" varchar(255) not null,
    "expiration" integer not null
);

alter table "cache_locks" add primary key ("key");
create index "cache_locks_expiration_index" on "cache_locks" ("expiration");
