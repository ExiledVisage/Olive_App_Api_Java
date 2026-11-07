-- Laravel: 2026_03_16_204142_create_stage_extension_types_table.php

create table "stage_extension_types" (
    "id" bigserial not null primary key,
    "key" varchar(255) not null,
    "name" varchar(255) not null,
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "stage_extension_types"
    add constraint "stage_extension_types_key_unique" unique ("key");
