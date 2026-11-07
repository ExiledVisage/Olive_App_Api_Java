-- Laravel: 2026_09_18_100003_create_suppliers_tables.php

create table "suppliers" (
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

create index "suppliers_is_active_index" on "suppliers" ("is_active");
alter table "suppliers" add constraint "suppliers_code_unique" unique ("code");

create table "supplier_fields" (
    "id" bigserial not null primary key,
    "supplier_id" bigint not null,
    "code" varchar(50) not null,
    "location" varchar(255) null,
    "area_decare" decimal(10, 2) null,
    "note" text null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "supplier_fields"
    add constraint "supplier_fields_supplier_id_foreign"
        foreign key ("supplier_id")
        references "suppliers" ("id")
        on delete cascade;

alter table "supplier_fields" add constraint "supplier_fields_code_unique" unique ("code");
alter table "production_batches" add column "supplier_id" bigint null;

alter table "production_batches"
    add constraint "production_batches_supplier_id_foreign"
        foreign key ("supplier_id")
        references "suppliers" ("id")
        on delete set null;

alter table "production_batches" add column "supplier_field_id" bigint null;

alter table "production_batches"
    add constraint "production_batches_supplier_field_id_foreign"
        foreign key ("supplier_field_id")
        references "supplier_fields" ("id")
        on delete set null;
