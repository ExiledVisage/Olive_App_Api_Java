-- Laravel: 2026_06_24_100014_create_orders_table.php

create table "orders" (
    "id" bigserial not null primary key,
    "customer_id" bigint not null,
    "delivery_address_id" bigint not null,
    "created_by_user_id" bigint null,
    "status" varchar(30) not null default 'draft',
    "order_date" date not null,
    "note" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "orders"
    add constraint "orders_customer_id_foreign"
        foreign key ("customer_id")
        references "customers" ("id")
        on delete restrict;

alter table "orders"
    add constraint "orders_delivery_address_id_foreign"
        foreign key ("delivery_address_id")
        references "customer_addresses" ("id")
        on delete restrict;

alter table "orders"
    add constraint "orders_created_by_user_id_foreign"
        foreign key ("created_by_user_id")
        references "users" ("id")
        on delete set null;

create index "orders_customer_id_index" on "orders" ("customer_id");
create index "orders_status_index" on "orders" ("status");
create index "orders_order_date_index" on "orders" ("order_date");
