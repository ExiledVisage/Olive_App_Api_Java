-- Laravel: 2026_06_24_100013_create_customer_addresses_table.php

create table "customer_addresses" (
    "id" bigserial not null primary key,
    "customer_id" bigint not null,
    "label" varchar(255) not null,
    "country" varchar(255) not null default 'TR',
    "city" varchar(255) not null,
    "district" varchar(255) null,
    "neighborhood" varchar(255) null,
    "street" varchar(255) null,
    "building_no" varchar(255) null,
    "apartment_no" varchar(255) null,
    "postal_code" varchar(255) null,
    "full_address" text null,
    "latitude" decimal(10, 7) null,
    "longitude" decimal(10, 7) null,
    "is_default" boolean not null default '0',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "customer_addresses"
    add constraint "customer_addresses_customer_id_foreign"
        foreign key ("customer_id")
        references "customers" ("id")
        on delete cascade;

create index "customer_addresses_customer_id_index" on "customer_addresses" ("customer_id");
