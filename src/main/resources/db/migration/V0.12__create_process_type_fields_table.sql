-- Laravel: 2026_03_09_225618_create_process_type_fields_table.php

create table "process_type_fields" (
    "id" bigserial not null primary key,
    "process_type_id" bigint not null,
    "field_key" varchar(255) not null,
    "label" varchar(255) not null,
    "data_type" varchar(255) not null,
    "is_required" boolean not null default '0',
    "unit" varchar(255) null,
    "sort_order" integer not null default '0',
    "is_active" boolean not null default '1',
    "options" json null,
    "default_value" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "process_type_fields"
    add constraint "process_type_fields_process_type_id_foreign"
        foreign key ("process_type_id")
        references "process_types" ("id")
        on delete cascade;

alter table "process_type_fields"
    add constraint "process_type_fields_process_type_id_field_key_unique"
        unique ("process_type_id", "field_key");
