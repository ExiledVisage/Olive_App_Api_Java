-- Laravel: 2026_03_06_142629_create_batch_processes_table.php

create table "batch_processes" (
    "id" bigserial not null primary key,
    "production_batch_id" bigint not null,
    "production_stage_id" bigint not null,
    "status" varchar(255) not null default 'waiting',
    "operator_id" bigint null,
    "started_at" timestamp(0) without time zone null,
    "completed_at" timestamp(0) without time zone null,
    "note" text null,
    "operation_data" json null,
    "submitted_by_user_id" bigint null,
    "submitted_at" timestamp(0) without time zone null,
    "approved_by_user_id" bigint null,
    "approved_at" timestamp(0) without time zone null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "batch_processes"
    add constraint "batch_processes_production_batch_id_foreign"
        foreign key ("production_batch_id")
        references "production_batches" ("id")
        on delete cascade;

alter table "batch_processes"
    add constraint "batch_processes_production_stage_id_foreign"
        foreign key ("production_stage_id")
        references "production_stages" ("id")
        on delete restrict;

alter table "batch_processes"
    add constraint "batch_processes_operator_id_foreign"
        foreign key ("operator_id")
        references "users" ("id")
        on delete set null;

alter table "batch_processes"
    add constraint "batch_processes_submitted_by_user_id_foreign"
        foreign key ("submitted_by_user_id")
        references "users" ("id")
        on delete set null;

alter table "batch_processes"
    add constraint "batch_processes_approved_by_user_id_foreign"
        foreign key ("approved_by_user_id")
        references "users" ("id")
        on delete set null;

alter table "batch_processes"
    add constraint "batch_processes_production_batch_id_production_stage_id_unique"
        unique ("production_batch_id", "production_stage_id");

create index "batch_processes_production_batch_id_status_index"
    on "batch_processes" ("production_batch_id", "status");

create index "batch_processes_operator_id_index" on "batch_processes" ("operator_id");
