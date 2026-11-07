-- Laravel: 2026_06_24_100011_create_finished_product_storage_stock_table.php

create table "finished_product_storage_stocks" (
    "id" bigserial not null primary key,
    "finished_product_storage_id" bigint not null,
    "finished_product_id" bigint not null,
    "quantity" integer not null default '0',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_storage_stocks"
    add constraint "finished_product_storage_stocks_finished_product_storage_id_foreign"
        foreign key ("finished_product_storage_id")
        references "finished_product_storages" ("id")
        on delete cascade;

alter table "finished_product_storage_stocks"
    add constraint "finished_product_storage_stocks_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete restrict;

alter table "finished_product_storage_stocks"
    add constraint "fp_storage_stock_unique"
        unique ("finished_product_storage_id", "finished_product_id");
