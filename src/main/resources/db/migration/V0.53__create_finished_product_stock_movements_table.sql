-- Laravel: 2026_06_24_100019_create_finished_product_stock_movements_table.php

create table "finished_product_stock_movements" (
    "id" bigserial not null primary key,
    "finished_product_storage_id" bigint not null,
    "finished_product_storage_slot_id" bigint not null,
    "finished_product_id" bigint not null,
    "movement_type" varchar(20) not null,
    "quantity" integer not null,
    "counterparty_slot_id" bigint null,
    "transfer_reference" uuid null,
    "created_by_user_id" bigint null,
    "note" text null,
    "moved_at" timestamp(0) without time zone not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_finished_product_storage_id_foreign"
        foreign key ("finished_product_storage_id")
        references "finished_product_storages" ("id")
        on delete restrict;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_finished_product_storage_slot_id_foreign"
        foreign key ("finished_product_storage_slot_id")
        references "finished_product_storage_slots" ("id")
        on delete restrict;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete restrict;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_counterparty_slot_id_foreign"
        foreign key ("counterparty_slot_id")
        references "finished_product_storage_slots" ("id")
        on delete set null;

alter table "finished_product_stock_movements"
    add constraint "finished_product_stock_movements_created_by_user_id_foreign"
        foreign key ("created_by_user_id")
        references "users" ("id")
        on delete set null;

create index "finished_product_stock_movements_finished_product_storage_id_moved_at_index"
    on "finished_product_stock_movements" ("finished_product_storage_id", "moved_at");

create index "finished_product_stock_movements_finished_product_id_moved_at_index"
    on "finished_product_stock_movements" ("finished_product_id", "moved_at");

create index "finished_product_stock_movements_transfer_reference_index"
    on "finished_product_stock_movements" ("transfer_reference");
