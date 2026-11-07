-- Laravel: 2026_06_24_100016_create_finished_product_storage_slot_stocks_table.php

create table "finished_product_storage_slot_stocks" (
    "id" bigserial not null primary key,
    "finished_product_storage_slot_id" bigint not null,
    "finished_product_id" bigint not null,
    "quantity" integer not null default '0',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_storage_slot_stocks"
    add constraint "finished_product_storage_slot_stocks_finished_product_storage_slot_id_foreign"
        foreign key ("finished_product_storage_slot_id")
        references "finished_product_storage_slots" ("id")
        on delete cascade;

alter table "finished_product_storage_slot_stocks"
    add constraint "finished_product_storage_slot_stocks_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete restrict;

alter table "finished_product_storage_slot_stocks"
    add constraint "fp_slot_stock_unique"
        unique ("finished_product_storage_slot_id", "finished_product_id");

create index "finished_product_storage_slot_stocks_finished_product_id_index"
    on "finished_product_storage_slot_stocks" ("finished_product_id");
