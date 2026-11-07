-- Laravel: 2026_06_24_100008_create_finished_product_run_attribute_values_table.php

create table "finished_product_run_attribute_values" (
    "id" bigserial not null primary key,
    "finished_product_run_id" bigint not null,
    "attribute_definition_id" bigint not null,
    "estimated_value" decimal(14, 4) not null,
    "actual_value" decimal(14, 4) null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "finished_product_run_attribute_values"
    add constraint "finished_product_run_attribute_values_finished_product_run_id_foreign"
        foreign key ("finished_product_run_id")
        references "finished_product_runs" ("id")
        on delete cascade;

alter table "finished_product_run_attribute_values"
    add constraint "finished_product_run_attribute_values_attribute_definition_id_foreign"
        foreign key ("attribute_definition_id")
        references "attribute_definitions" ("id")
        on delete restrict;

alter table "finished_product_run_attribute_values"
    add constraint "fp_run_attr_value_unique"
        unique ("finished_product_run_id", "attribute_definition_id");
