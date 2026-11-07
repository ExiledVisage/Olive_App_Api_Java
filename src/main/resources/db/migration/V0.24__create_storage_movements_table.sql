-- Laravel: 2026_03_23_223224_create_storage_movements_table.php

create table "storage_movements" (
    "id" bigserial not null primary key,
    "storage_unit_id" bigint not null,
    "material_id" bigint not null,
    "batch_process_id" bigint null,
    "counterparty_storage_unit_id" bigint null,
    "source_type" varchar(50) not null,
    "movement_type" varchar(50) not null,
    "transfer_reference" varchar(255) null,
    "quantity" decimal(14, 2) not null,
    "unit" varchar(255) check ("unit" in ('KG', 'LITRE')) not null,
    "note" text null,
    "moved_at" timestamp(0) without time zone null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "storage_movements"
    add constraint "storage_movements_storage_unit_id_foreign"
        foreign key ("storage_unit_id")
        references "storage_units" ("id")
        on delete restrict;

alter table "storage_movements"
    add constraint "storage_movements_material_id_foreign"
        foreign key ("material_id")
        references "materials" ("id")
        on delete restrict;

alter table "storage_movements"
    add constraint "storage_movements_batch_process_id_foreign"
        foreign key ("batch_process_id")
        references "batch_processes" ("id")
        on delete set null;

alter table "storage_movements"
    add constraint "storage_movements_counterparty_storage_unit_id_foreign"
        foreign key ("counterparty_storage_unit_id")
        references "storage_units" ("id")
        on delete set null;

create index "storage_movements_storage_unit_id_movement_type_index"
    on "storage_movements" ("storage_unit_id", "movement_type");

create index "storage_movements_material_id_index" on "storage_movements" ("material_id");
create index "storage_movements_batch_process_id_index" on "storage_movements" ("batch_process_id");
create index "storage_movements_source_type_index" on "storage_movements" ("source_type");
create index "storage_movements_moved_at_index" on "storage_movements" ("moved_at");
