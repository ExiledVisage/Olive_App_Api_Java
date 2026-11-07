-- Laravel: 2026_03_16_204144_create_production_stage_extension_values_table.php

create table "production_stage_extension_values" (
    "id" bigserial not null primary key,
    "production_stage_extension_id" bigint not null,
    "stage_extension_type_field_id" bigint not null,
    "value_boolean" boolean null,
    "value_integer" bigint null,
    "value_decimal" decimal(14, 4) null,
    "value_text" varchar(255) null,
    "value_json" json null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_stage_extension_values"
    add constraint "production_stage_extension_values_production_stage_extension_id_foreign"
        foreign key ("production_stage_extension_id")
        references "production_stage_extensions" ("id")
        on delete cascade;

alter table "production_stage_extension_values"
    add constraint "production_stage_extension_values_stage_extension_type_field_id_foreign"
        foreign key ("stage_extension_type_field_id")
        references "stage_extension_type_fields" ("id")
        on delete restrict;

alter table "production_stage_extension_values"
    add constraint "prod_stage_ext_value_unique"
        unique ("production_stage_extension_id", "stage_extension_type_field_id");
