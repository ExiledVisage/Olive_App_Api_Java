-- Laravel: 2026_06_24_100015_create_order_items_table.php

create table "order_items" (
    "id" bigserial not null primary key,
    "order_id" bigint not null,
    "finished_product_id" bigint not null,
    "quantity" integer not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "order_items"
    add constraint "order_items_order_id_foreign"
        foreign key ("order_id")
        references "orders" ("id")
        on delete cascade;

alter table "order_items"
    add constraint "order_items_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete restrict;

alter table "order_items"
    add constraint "order_item_unique" unique ("order_id", "finished_product_id");
