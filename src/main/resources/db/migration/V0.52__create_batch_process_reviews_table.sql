-- Laravel: 2026_06_24_100018_create_batch_process_reviews_table.php

create table "batch_process_reviews" (
    "id" bigserial not null primary key,
    "batch_process_id" bigint not null,
    "decision" varchar(20) not null,
    "reviewed_by_user_id" bigint null,
    "submitted_by_user_id" bigint null,
    "operation_data" json null,
    "note" text null,
    "reviewed_at" timestamp(0) without time zone not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "batch_process_reviews"
    add constraint "batch_process_reviews_batch_process_id_foreign"
        foreign key ("batch_process_id")
        references "batch_processes" ("id")
        on delete cascade;

alter table "batch_process_reviews"
    add constraint "batch_process_reviews_reviewed_by_user_id_foreign"
        foreign key ("reviewed_by_user_id")
        references "users" ("id")
        on delete set null;

alter table "batch_process_reviews"
    add constraint "batch_process_reviews_submitted_by_user_id_foreign"
        foreign key ("submitted_by_user_id")
        references "users" ("id")
        on delete set null;

create index "batch_process_reviews_batch_process_id_reviewed_at_index"
    on "batch_process_reviews" ("batch_process_id", "reviewed_at");
