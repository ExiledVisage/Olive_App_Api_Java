-- Laravel: 2026_03_23_223222_create_materials_table.php

create table "materials" (
    "id" bigserial not null primary key,
    "production_batch_id" bigint null,
    "production_product_type_id" bigint not null,
    "parent_material_id" bigint null,
    "origin_type" varchar(50) not null,
    "quantity" decimal(14, 4) not null default '0',
    "unit" varchar(255) check ("unit" in ('KG', 'LITRE')) not null,
    "status" varchar(30) not null default 'active',
    "current_location_type" varchar(50) null,
    "current_location_id" bigint null,
    "source_production_stage_id" bigint null,
    "created_by_batch_process_id" bigint null,
    "closed_by_batch_process_id" bigint null,
    "note" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "materials"
    add constraint "materials_production_batch_id_foreign"
        foreign key ("production_batch_id")
        references "production_batches" ("id")
        on delete set null;

alter table "materials"
    add constraint "materials_production_product_type_id_foreign"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;

alter table "materials"
    add constraint "materials_parent_material_id_foreign"
        foreign key ("parent_material_id")
        references "materials" ("id")
        on delete set null;

alter table "materials"
    add constraint "materials_source_production_stage_id_foreign"
        foreign key ("source_production_stage_id")
        references "production_stages" ("id")
        on delete set null;

alter table "materials"
    add constraint "materials_created_by_batch_process_id_foreign"
        foreign key ("created_by_batch_process_id")
        references "batch_processes" ("id")
        on delete set null;

alter table "materials"
    add constraint "materials_closed_by_batch_process_id_foreign"
        foreign key ("closed_by_batch_process_id")
        references "batch_processes" ("id")
        on delete set null;

create index "materials_production_batch_id_status_index"
    on "materials" ("production_batch_id", "status");

create index "materials_production_product_type_id_index"
    on "materials" ("production_product_type_id");

create index "materials_parent_material_id_index" on "materials" ("parent_material_id");

create index "materials_current_location_type_current_location_id_index"
    on "materials" ("current_location_type", "current_location_id");

create index "materials_source_production_stage_id_index"
    on "materials" ("source_production_stage_id");

create index "materials_created_by_batch_process_id_index"
    on "materials" ("created_by_batch_process_id");

create index "materials_closed_by_batch_process_id_index"
    on "materials" ("closed_by_batch_process_id");

create index "materials_origin_type_index" on "materials" ("origin_type");
