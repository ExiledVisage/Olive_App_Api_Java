-- Laravel: 2026_04_02_151109_create_production_stage_extension_output_production_products_table.php

create table "production_stage_extension_output_production_products" (
    "id" bigserial not null primary key,
    "production_stage_extension_id" bigint not null,
    "production_product_id" bigint not null,
    "sort_order" integer not null default '1',
    "is_required" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_stage_extension_output_production_products"
    add constraint "prod_stage_ext_output_ext_fk"
        foreign key ("production_stage_extension_id")
        references "production_stage_extensions" ("id")
        on delete cascade;

alter table "production_stage_extension_output_production_products"
    add constraint "prod_stage_ext_output_product_fk"
        foreign key ("production_product_id")
        references "production_products" ("id")
        on delete restrict;

alter table "production_stage_extension_output_production_products"
    add constraint "prod_stage_ext_output_product_type_unique"
        unique ("production_stage_extension_id", "production_product_id");
