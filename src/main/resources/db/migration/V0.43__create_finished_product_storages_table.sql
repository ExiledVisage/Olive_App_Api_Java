-- Laravel: 2026_06_24_100009_create_finished_product_storages_table.php

create table "finished_product_storages" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "description" text null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);
