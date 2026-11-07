-- Laravel: 2026_03_09_225758_create_batch_process_field_values_table.php

create table "batch_process_field_values" (
    "id" bigserial not null primary key,
    "batch_process_id" bigint not null,
    "process_type_field_id" bigint not null,
    "value_text" text null,
    "value_decimal" decimal(15, 4) null,
    "value_integer" bigint null,
    "value_boolean" boolean null,
    "value_datetime" timestamp(0) without time zone null,
    "value_json" json null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "batch_process_field_values"
    add constraint "batch_process_field_values_batch_process_id_foreign"
        foreign key ("batch_process_id")
        references "batch_processes" ("id")
        on delete cascade;

alter table "batch_process_field_values"
    add constraint "batch_process_field_values_process_type_field_id_foreign"
        foreign key ("process_type_field_id")
        references "process_type_fields" ("id")
        on delete cascade;

alter table "batch_process_field_values"
    add constraint "batch_process_field_values_batch_process_id_process_type_field_id_unique"
        unique ("batch_process_id", "process_type_field_id");
