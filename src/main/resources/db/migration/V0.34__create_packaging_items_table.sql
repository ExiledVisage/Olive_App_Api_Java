-- Laravel: 2026_06_24_100000_create_packaging_items_table.php

create table "packaging_items" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "type" varchar(30) not null,
    "current_stock" integer not null default '0',
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);
