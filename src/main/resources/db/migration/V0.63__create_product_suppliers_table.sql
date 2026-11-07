-- Laravel: 2026_10_01_100000_create_product_suppliers_table.php

create table "product_suppliers" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "code" varchar(50) not null,
    "phone" varchar(50) null,
    "email" varchar(255) null,
    "address" text null,
    "note" text null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

create index "product_suppliers_is_active_index" on "product_suppliers" ("is_active");
alter table "product_suppliers" add constraint "product_suppliers_code_unique" unique ("code");
