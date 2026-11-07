-- Laravel: 2026_06_24_100017_create_order_status_histories_table.php

create table "order_status_histories" (
    "id" bigserial not null primary key,
    "order_id" bigint not null,
    "from_status" varchar(30) null,
    "to_status" varchar(30) not null,
    "changed_by_user_id" bigint null,
    "note" text null,
    "changed_at" timestamp(0) without time zone not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "order_status_histories"
    add constraint "order_status_histories_order_id_foreign"
        foreign key ("order_id")
        references "orders" ("id")
        on delete cascade;

alter table "order_status_histories"
    add constraint "order_status_histories_changed_by_user_id_foreign"
        foreign key ("changed_by_user_id")
        references "users" ("id")
        on delete set null;

create index "order_status_histories_order_id_changed_at_index"
    on "order_status_histories" ("order_id", "changed_at");

create index "order_status_histories_to_status_index" on "order_status_histories" ("to_status");
