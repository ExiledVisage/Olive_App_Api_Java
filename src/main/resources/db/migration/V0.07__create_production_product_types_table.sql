-- Laravel: 2026_03_06_122908_create_production_product_types_table.php

create table "production_product_types" (
    "id" bigserial not null primary key,
    "production_product_id" bigint not null,
    "name" varchar(255) not null,
    "code" varchar(255) not null,
    "type" varchar(255) null,
    "unit" varchar(255) check ("unit" in ('KG', 'LITRE')) not null default 'LITRE',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_product_types"
    add constraint "production_product_types_production_product_id_foreign"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete restrict;

alter table "production_product_types"
    add constraint "production_product_types_code_unique" unique ("code");
