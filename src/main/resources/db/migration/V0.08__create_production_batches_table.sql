-- Laravel: 2026_03_06_123410_create_production_batches_table.php

create table "production_batches" (
    "id" bigserial not null primary key,
    "batch_code" varchar(255) not null,
    "production_line_id" bigint not null,
    "created_by_user_id" bigint null,
    "status" varchar(255) not null default 'pending',
    "quantity_kg" decimal(14, 4) null,
    "received_at" timestamp(0) without time zone null,
    "started_at" timestamp(0) without time zone null,
    "completed_at" timestamp(0) without time zone null,
    "note" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_batches"
    add constraint "production_batches_production_line_id_foreign"
        foreign key ("production_line_id")
        references "production_lines" ("id")
        on delete restrict;

alter table "production_batches"
    add constraint "production_batches_created_by_user_id_foreign"
        foreign key ("created_by_user_id")
        references "users" ("id")
        on delete set null;

create index "production_batches_production_line_id_status_index"
    on "production_batches" ("production_line_id", "status");

create index "production_batches_created_by_user_id_index"
    on "production_batches" ("created_by_user_id");

alter table "production_batches"
    add constraint "production_batches_batch_code_unique" unique ("batch_code");
