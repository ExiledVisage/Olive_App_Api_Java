-- Laravel: 2026_03_16_204143_create_stage_extension_type_fields_table.php

create table "stage_extension_type_fields" (
    "id" bigserial not null primary key,
    "stage_extension_type_id" bigint not null,
    "field_key" varchar(255) not null,
    "label" varchar(255) not null,
    "data_type" varchar(255) not null,
    "is_required" boolean not null default '0',
    "sort_order" integer not null default '1',
    "default_value_boolean" boolean null,
    "default_value_integer" bigint null,
    "default_value_decimal" decimal(14, 4) null,
    "default_value_text" varchar(255) null,
    "default_value_json" json null,
    "options_json" json null,
    "description" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "stage_extension_type_fields"
    add constraint "stage_extension_type_fields_stage_extension_type_id_foreign"
        foreign key ("stage_extension_type_id")
        references "stage_extension_types" ("id")
        on delete cascade;

alter table "stage_extension_type_fields"
    add constraint "stage_ext_type_field_unique" unique ("stage_extension_type_id", "field_key");
