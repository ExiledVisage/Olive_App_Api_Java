-- Laravel: 2026_03_16_204143_create_production_stage_extensions_table.php

create table "production_stage_extensions" (
    "id" bigserial not null primary key,
    "production_stage_id" bigint not null,
    "stage_extension_type_id" bigint not null,
    "is_required" boolean not null default '0',
    "is_enabled" boolean not null default '1',
    "sort_order" integer not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_stage_extensions"
    add constraint "production_stage_extensions_production_stage_id_foreign"
        foreign key ("production_stage_id")
        references "production_stages" ("id")
        on delete cascade;

alter table "production_stage_extensions"
    add constraint "production_stage_extensions_stage_extension_type_id_foreign"
        foreign key ("stage_extension_type_id")
        references "stage_extension_types" ("id")
        on delete restrict;

alter table "production_stage_extensions"
    add constraint "prod_stage_ext_unique"
        unique ("production_stage_id", "stage_extension_type_id");
