-- Laravel: 2026_06_24_100005_create_finished_product_components_table.php

create table "finished_product_components" (
    "id" bigserial not null primary key,
    "finished_product_id" bigint not null,
    "packaging_item_id" bigint not null,
    "quantity" integer not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_components"
    add constraint "finished_product_components_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete cascade;

alter table "finished_product_components"
    add constraint "finished_product_components_packaging_item_id_foreign"
        foreign key ("packaging_item_id")
        references "packaging_items" ("id")
        on delete restrict;

alter table "finished_product_components"
    add constraint "fp_component_unique" unique ("finished_product_id", "packaging_item_id");
