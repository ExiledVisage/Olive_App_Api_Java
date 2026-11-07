-- Laravel: 2026_04_09_194519_create_storage_unit_attribute_rules_table.php

create table "storage_unit_attribute_rules" (
    "id" bigserial not null primary key,
    "storage_unit_id" bigint not null,
    "production_product_attribute_definition_id" bigint not null,
    "min_value_decimal" decimal(15, 4) null,
    "max_value_decimal" decimal(15, 4) null,
    "apply_mode" varchar(255)
        check ("apply_mode" in (
            'incoming_only', 'tank_average_only', 'incoming_and_average'
        )) not null default 'incoming_and_average',
    "is_active" boolean not null default '1',
    "note" text null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "storage_unit_attribute_rules"
    add constraint "storage_unit_attribute_rules_storage_unit_id_foreign"
        foreign key ("storage_unit_id")
        references "storage_units" ("id")
        on delete cascade;

alter table "storage_unit_attribute_rules"
    add constraint "storage_unit_attribute_rules_production_product_attribute_definition_id_foreign"
        foreign key ("production_product_attribute_definition_id")
        references "production_product_attribute_definitions" ("id")
        on delete restrict;

alter table "storage_unit_attribute_rules"
    add constraint "storage_unit_attr_rules_unique"
        unique ("storage_unit_id", "production_product_attribute_definition_id");
