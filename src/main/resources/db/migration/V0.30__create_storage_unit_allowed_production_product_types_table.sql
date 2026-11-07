-- Laravel: 2026_04_09_161254_create_storage_unit_allowed_production_product_types_table.php

create table "storage_unit_allowed_production_product_types" (
    "id" bigserial not null primary key,
    "storage_unit_id" bigint not null,
    "production_product_type_id" bigint not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "storage_unit_allowed_production_product_types"
    add constraint "storage_unit_allowed_production_product_types_storage_unit_id_foreign"
        foreign key ("storage_unit_id")
        references "storage_units" ("id")
        on delete cascade;

alter table "storage_unit_allowed_production_product_types"
    add constraint "storage_unit_allowed_production_product_types_production_product_type_id_foreign"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;

alter table "storage_unit_allowed_production_product_types"
    add constraint "storage_unit_allowed_production_product_types_storage_unit_id_production_product_type_id_unique"
        unique ("storage_unit_id", "production_product_type_id");
