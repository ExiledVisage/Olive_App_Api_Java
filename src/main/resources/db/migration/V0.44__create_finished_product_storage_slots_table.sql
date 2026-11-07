-- Laravel: 2026_06_24_100010_create_finished_product_storage_slots_table.php

create table "finished_product_storage_slots" (
    "id" bigserial not null primary key,
    "finished_product_storage_id" bigint not null,
    "name" varchar(50) null,
    "volume_litre" decimal(10, 4) not null,
    "max_quantity" integer not null,
    "current_quantity" integer not null default '0',
    "finished_product_id" bigint null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_storage_slots"
    add constraint "finished_product_storage_slots_finished_product_storage_id_foreign"
        foreign key ("finished_product_storage_id")
        references "finished_product_storages" ("id")
        on delete cascade;

alter table "finished_product_storage_slots"
    add constraint "finished_product_storage_slots_finished_product_id_foreign"
        foreign key ("finished_product_id")
        references "finished_products" ("id")
        on delete set null;

create index "finished_product_storage_slots_finished_product_storage_id_volume_litre_index"
    on "finished_product_storage_slots" ("finished_product_storage_id", "volume_litre");
