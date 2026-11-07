-- Laravel: 2026_06_24_100012_create_customers_table.php

create table "customers" (
    "id" bigserial not null primary key,
    "name" varchar(255) not null,
    "company_name" varchar(255) null,
    "phone" varchar(255) null,
    "email" varchar(255) null,
    "notes" text null,
    "is_active" boolean not null default '1',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);
