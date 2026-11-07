-- Laravel: 2026_03_16_204145_create_storage_unit_balances_table.php

create table "storage_unit_balances" (
    "id" bigserial not null primary key,
    "storage_unit_id" bigint not null,
    "production_product_id" bigint not null,
    "current_quantity" decimal(14, 2) not null default '0',
    "unit" varchar(255) check ("unit" in ('KG', 'LITRE')) not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "storage_unit_balances"
    add constraint "storage_unit_balances_storage_unit_id_foreign"
        foreign key ("storage_unit_id")
        references "storage_units" ("id")
        on delete cascade;

alter table "storage_unit_balances"
    add constraint "storage_unit_balances_production_product_id_foreign"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete restrict;

alter table "storage_unit_balances"
    add constraint "storage_unit_balances_storage_unit_id_production_product_id_unit_unique"
        unique ("storage_unit_id", "production_product_id", "unit");
