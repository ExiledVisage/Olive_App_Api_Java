-- Laravel: 2026_04_01_151301_create_production_line_input_production_product_types_table.php

create table "production_line_input_production_products" (
    "id" bigserial not null primary key,
    "production_line_id" bigint not null,
    "production_product_id" bigint not null,
    "sort_order" integer not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_line_input_production_products"
    add constraint "production_line_input_production_products_production_line_id_foreign"
        foreign key ("production_line_id")
        references "production_lines" ("id")
        on delete cascade;

alter table "production_line_input_production_products"
    add constraint "production_line_input_production_products_production_product_id_foreign"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete restrict;

alter table "production_line_input_production_products"
    add constraint "production_line_input_product_type_unique"
        unique ("production_line_id", "production_product_id");
