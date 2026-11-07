-- Laravel: 2026_04_04_014548_create_storage_unit_allowed_production_products_table.php

create table "storage_unit_allowed_production_products" (
    "id" bigserial not null primary key,
    "storage_unit_id" bigint not null,
    "production_product_id" bigint not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "storage_unit_allowed_production_products"
    add constraint "storage_unit_allowed_production_products_storage_unit_id_foreign"
        foreign key ("storage_unit_id")
        references "storage_units" ("id")
        on delete cascade;

alter table "storage_unit_allowed_production_products"
    add constraint "storage_unit_allowed_production_products_production_product_id_foreign"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete restrict;

alter table "storage_unit_allowed_production_products"
    add constraint "storage_unit_allowed_production_products_storage_unit_id_production_product_id_unique"
        unique ("storage_unit_id", "production_product_id");
