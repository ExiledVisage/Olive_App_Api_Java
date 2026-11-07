-- Laravel: 2026_03_06_120000_create_production_products_table.php

create table "production_products" (
    "id" bigserial not null primary key,
    "key" varchar(255) not null,
    "name" varchar(255) not null,
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_products" add constraint "production_products_key_unique" unique ("key");
