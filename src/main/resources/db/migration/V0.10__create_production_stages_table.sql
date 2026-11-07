-- Laravel: 2026_03_06_142541_create_production_stages_table.php

create table "production_stages" (
    "id" bigserial not null primary key,
    "production_line_id" bigint not null,
    "process_type_id" bigint null,
    "name" varchar(255) null,
    "stage_order" integer not null,
    "is_required" boolean not null default '1',
    "requires_approval" boolean not null default '0',
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "production_stages"
    add constraint "production_stages_production_line_id_foreign"
        foreign key ("production_line_id")
        references "production_lines" ("id")
        on delete cascade;

alter table "production_stages"
    add constraint "production_stages_process_type_id_foreign"
        foreign key ("process_type_id")
        references "process_types" ("id")
        on delete restrict;

alter table "production_stages"
    add constraint "production_stages_production_line_id_stage_order_unique"
        unique ("production_line_id", "stage_order");
