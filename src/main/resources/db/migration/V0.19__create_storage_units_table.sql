-- Laravel: 2026_03_16_204144_create_storage_units_table.php

create table "storage_units" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "code" varchar(255) not null,
    "capacity" decimal(14, 2) not null,
    "capacity_unit" varchar(255) check ("capacity_unit" in ('KG', 'LITRE')) not null,
    "is_blend_tank" boolean not null default '0',
    "allow_multiple_production_product_types" boolean not null default '0',
    "allow_multiple_production_products" boolean not null default '0',
    "description" text null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

create index "storage_units_is_active_index" on "storage_units" ("is_active");
create index "storage_units_is_blend_tank_index" on "storage_units" ("is_blend_tank");

create index "storage_units_allow_multiple_production_product_types_index"
    on "storage_units" ("allow_multiple_production_product_types");

create index "storage_units_allow_multiple_production_products_index"
    on "storage_units" ("allow_multiple_production_products");

alter table "storage_units" add constraint "storage_units_code_unique" unique ("code");
