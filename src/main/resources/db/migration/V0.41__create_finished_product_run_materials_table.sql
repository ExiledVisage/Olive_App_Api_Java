-- Laravel: 2026_06_24_100007_create_finished_product_run_materials_table.php

create table "finished_product_run_materials" (
    "id" bigserial not null primary key,
    "finished_product_run_id" bigint not null,
    "source_material_id" bigint not null,
    "quantity_used" decimal(14, 4) not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_run_materials"
    add constraint "finished_product_run_materials_finished_product_run_id_foreign"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete cascade;

alter table "finished_product_run_materials"
    add constraint "finished_product_run_materials_source_material_id_foreign"
        foreign key ("source_material_id")
        references "materials" ("id")
        on delete restrict;

create index "finished_product_run_materials_finished_product_run_id_index"
    on "finished_product_run_materials" ("finished_product_run_id");

create index "finished_product_run_materials_source_material_id_index"
    on "finished_product_run_materials" ("source_material_id");
