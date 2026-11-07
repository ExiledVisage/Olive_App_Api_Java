-- Laravel: 2026_06_24_100001_create_finished_product_recipes_table.php

create table "finished_product_recipes" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "version" integer not null default '1',
    "is_active" boolean not null default '1',
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

create index "finished_product_recipes_name_version_index"
    on "finished_product_recipes" ("name", "version");

create index "finished_product_recipes_is_active_index" on "finished_product_recipes" ("is_active");
