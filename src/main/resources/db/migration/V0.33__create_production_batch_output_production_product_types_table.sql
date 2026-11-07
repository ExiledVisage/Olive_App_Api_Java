-- Laravel: 2026_04_22_221543_create_production_batch_output_production_product_types_table.php

create table "production_batch_output_production_product_types" (
    "id" bigserial not null primary key,
    "production_batch_id" bigint not null,
    "production_product_type_id" bigint not null,
    "sort_order" integer not null default '0',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_batch_output_production_product_types"
    add constraint "prod_batch_output_batch_fk"
        foreign key ("production_batch_id")
        references "production_batches" ("id")
        on delete cascade;

alter table "production_batch_output_production_product_types"
    add constraint "prod_batch_output_type_fk"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;

create index "prod_batch_output_batch_idx"
    on "production_batch_output_production_product_types" ("production_batch_id");

create index "prod_batch_output_type_idx"
    on "production_batch_output_production_product_types" ("production_product_type_id");
