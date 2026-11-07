-- Laravel: 2026_10_01_100001_create_packaging_lot_tables.php
--
-- Migration'daki veri komutlari (mevcut kayitlarin doldurulmasi) alinmadi; bos veritabaninda
-- etkisizdir.

create table "packaging_lots" (
    "id" bigserial not null primary key,
    "packaging_item_id" bigint not null,
    "product_supplier_id" bigint null,
    "lot_code" varchar(100) not null,
    "received_at" timestamp(0) without time zone not null,
    "quantity_received" integer not null,
    "quantity_remaining" integer not null,
    "status" varchar(20) not null default 'active',
    "note" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "packaging_lots"
    add constraint "packaging_lots_packaging_item_id_foreign"
        foreign key ("packaging_item_id")
        references "packaging_items" ("id")
        on delete restrict;

alter table "packaging_lots"
    add constraint "packaging_lots_product_supplier_id_foreign"
        foreign key ("product_supplier_id")
        references "product_suppliers" ("id")
        on delete set null;

alter table "packaging_lots"
    add constraint "pkg_lots_item_lot_unique" unique ("packaging_item_id", "lot_code");

create index "pkg_lots_item_status_idx" on "packaging_lots" ("packaging_item_id", "status");
create index "pkg_lots_supplier_idx" on "packaging_lots" ("product_supplier_id");
create index "pkg_lots_received_idx" on "packaging_lots" ("received_at");

create table "packaging_lot_movements" (
    "id" bigserial not null primary key,
    "packaging_lot_id" bigint not null,
    "packaging_item_id" bigint not null,
    "finished_product_run_id" bigint null,
    "user_id" bigint null,
    "source_type" varchar(50) not null,
    "movement_type" varchar(50) not null,
    "quantity" integer not null,
    "note" text null,
    "moved_at" timestamp(0) without time zone not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "packaging_lot_movements"
    add constraint "packaging_lot_movements_packaging_lot_id_foreign"
        foreign key ("packaging_lot_id")
        references "packaging_lots" ("id")
        on delete restrict;

alter table "packaging_lot_movements"
    add constraint "packaging_lot_movements_packaging_item_id_foreign"
        foreign key ("packaging_item_id")
        references "packaging_items" ("id")
        on delete restrict;

alter table "packaging_lot_movements"
    add constraint "packaging_lot_movements_finished_product_run_id_foreign"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete set null;

alter table "packaging_lot_movements"
    add constraint "packaging_lot_movements_user_id_foreign"
        foreign key ("user_id")
        references "users" ("id")
        on delete set null;

create index "pkg_lot_mov_lot_time_idx"
    on "packaging_lot_movements" ("packaging_lot_id", "moved_at");

create index "pkg_lot_mov_item_idx" on "packaging_lot_movements" ("packaging_item_id");
create index "pkg_lot_mov_run_idx" on "packaging_lot_movements" ("finished_product_run_id");
create index "pkg_lot_mov_type_idx" on "packaging_lot_movements" ("movement_type");

create table "finished_product_run_packagings" (
    "id" bigserial not null primary key,
    "finished_product_run_id" bigint not null,
    "packaging_item_id" bigint not null,
    "packaging_lot_id" bigint not null,
    "quantity" integer not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_run_packagings"
    add constraint "fpr_pkg_run_fk"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete cascade;

alter table "finished_product_run_packagings"
    add constraint "finished_product_run_packagings_packaging_item_id_foreign"
        foreign key ("packaging_item_id")
        references "packaging_items" ("id")
        on delete restrict;

alter table "finished_product_run_packagings"
    add constraint "finished_product_run_packagings_packaging_lot_id_foreign"
        foreign key ("packaging_lot_id")
        references "packaging_lots" ("id")
        on delete restrict;

create index "fpr_pkg_run_idx" on "finished_product_run_packagings" ("finished_product_run_id");

alter table "finished_product_run_packagings"
    add constraint "fpr_pkg_run_lot_unique" unique ("finished_product_run_id", "packaging_lot_id");
