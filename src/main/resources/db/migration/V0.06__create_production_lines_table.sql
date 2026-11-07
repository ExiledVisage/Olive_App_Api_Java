-- Laravel: 2026_03_06_120001_create_production_lines_table.php

create table "production_lines" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);
