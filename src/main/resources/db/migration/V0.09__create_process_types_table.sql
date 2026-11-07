-- Laravel: 2026_03_06_141135_create_process_types_table.php

create table "process_types" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "description" text null,
    "category" varchar(255) not null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);
